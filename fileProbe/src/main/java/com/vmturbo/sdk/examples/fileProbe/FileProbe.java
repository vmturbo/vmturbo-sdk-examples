package com.vmturbo.sdk.examples.fileProbe;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.vmturbo.platform.common.dto.CommodityDTO;
import com.vmturbo.platform.common.dto.ModelEnum;
import com.vmturbo.platform.common.dto.ModelEnum.Commodity;
import com.vmturbo.platform.common.dto.ModelEnum.Entity;
import com.vmturbo.platform.common.dto.ModelEnum.TemplateType;
import com.vmturbo.platform.sdk.common.DTO.EntityDTO;
import com.vmturbo.platform.sdk.common.DTO.Provider;
import com.vmturbo.platform.sdk.common.DTO.ProviderType;
import com.vmturbo.platform.sdk.common.DTO.TemplateDTO;
import com.vmturbo.platform.sdk.common.DTO.TemplateDTO.TemplateCommodity;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntry;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntryType;
import com.vmturbo.platform.sdk.common.util.ResponseCode;
import com.vmturbo.platform.sdk.common.util.TargetValidationResponse;
import com.vmturbo.platform.sdk.probe.AbstractProbe;

/**
 * A sample implementation of the file probe. This probe reads data from an XML topology file and
 * generates entity DTOs for the target.
 */
public class FileProbe extends AbstractProbe {

    private static final Logger logger = Logger
                    .getLogger("com.vmturbo.platform.container.mediation");
    private static final String LOGPREFIX = "-- FileProbeExample -- : ";
    private static final String ABSTRACTION = "Abstraction:";
    private static final String NETWORKING = "Networking:";
    private static final String ATTR_ENTITY_TYPE = "xsi:type";
    private static final String ATTR_UUID = "uuid";
    private static final String ATTR_DISP_NAME = "displayName";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_CONSUMES = "Consumes";
    private static final String KEY = "key";
    private static final String ATTR_CAPACITY = "capacity";
    private static final String ATTR_USED = "used";
    private static final String ATTR_MAIN_MARKET = "mainMarket";
    private static final String TAG_COMM = "Commodities";
    private static final String TAG_COMM_BOUGHT = "CommoditiesBought";
    private static final String TAG_ENTITIES = "ServiceEntities";
    private static final String TAG_MARKET = "Analysis:Market";

    /**
     * Set of commodities that require a key.
     */
    private static final Set<Commodity> commWithKeySet = Sets
                    .newHashSet(Commodity.StorageClusterCommodity, Commodity.DatastoreCommodity,
                                Commodity.DSPMAccessCommodity, Commodity.Extent,
                                Commodity.ClusterCommodity, Commodity.NetworkCommodity,
                                Commodity.DataCenterCommodity, Commodity.ApplicationCommodity);

    /**
     * Map of entity's uuid indexed by commodity uuid.
     */
    private final Map<String, String> commToSeMap = new HashMap<String, String>();

    /**
     * Map of commodity bought map indexed by entity DTO. The commodity bought map is a map of
     * commodity DTO indexed by the consumes uuid
     */
    private final Map<EntityDTO, Map<String, CommodityDTO>> edToConsCommBoughtMap = new HashMap<EntityDTO, Map<String, CommodityDTO>>();

    /**
     * Generate an entity DTO from an element in XML
     * 
     * @param elem Element in XML
     * @return An EntityDTO parsed from the element in XML.
     */
    private EntityDTO generateEntityDTO(Element elem) {
        EntityDTO ed = new EntityDTO(null, null, null, null, null);
        String entityType = elem.getAttribute(ATTR_ENTITY_TYPE);
        if (entityType.startsWith(ABSTRACTION)) {
            entityType = entityType.replaceFirst(ABSTRACTION, "");
        } else if (entityType.startsWith(NETWORKING)) {
            entityType = entityType.replaceFirst(NETWORKING, "");
        }

        ed.setEntity(Entity.Unknown);
        for (Entity en : Entity.values()) {
            if (entityType.equals(en.toString())) {
                ed.setEntity(en);
                break;
            }
        }

        ed.setId(elem.getAttribute(ATTR_UUID));

        // If no "dispalyName" tag found, use "name" tag
        String name = elem.getAttribute(ATTR_DISP_NAME);
        if (name.equals(""))
            name = elem.getAttribute(ATTR_NAME);
        ed.setDisplayName(name);

        // Construct the commodity objects from the topology file
        List<CommodityDTO> commList = Lists.newArrayList();
        NodeList commNodeList = elem.getElementsByTagName(TAG_COMM);
        int commNodeListLen = commNodeList.getLength();
        for (int k = 0; k < commNodeListLen; k++) {
            Node commNode = commNodeList.item(k);
            if (commNode.getNodeType() == Node.ELEMENT_NODE) {
                CommodityDTO cd = generateCommDTO((Element)commNode);
                commList.add(cd);
                String uuid = ((Element)commNode).getAttribute(ATTR_UUID);
                commToSeMap.put(uuid, ed.getId());
            }
        }

        ed.setSold(commList);

        // Construct the commodities bought objects from the topology file
        Map<String, CommodityDTO> consumesToCommMap = new HashMap<String, CommodityDTO>();
        edToConsCommBoughtMap.put(ed, consumesToCommMap);
        commNodeList = elem.getElementsByTagName(TAG_COMM_BOUGHT);
        commNodeListLen = commNodeList.getLength();
        for (int k = 0; k < commNodeListLen; k++) {
            Node commNode = commNodeList.item(k);
            if (commNode.getNodeType() == Node.ELEMENT_NODE) {
                CommodityDTO cd = generateCommDTO((Element)commNode);
                String consUuid = ((Element)commNode).getAttribute(ATTR_CONSUMES);//consumesUuid;
                consumesToCommMap.put(consUuid, cd);
            }
        }

        return ed;
    }

    /**
     * Generate a commodity DTO from an element in XML.
     * 
     * @param elem Element in XML
     * @return An CommodityDTO parsed from the element in XML.
     */
    private CommodityDTO generateCommDTO(Element elem) {
        CommodityDTO cd = new CommodityDTO(null, null, 0f, 0f);

        // Set Type
        String commType = elem.getAttribute(ATTR_ENTITY_TYPE).replaceFirst(ABSTRACTION, "");

        cd.setCommodityClass(Commodity.Unknown);
        for (Commodity co : Commodity.values()) {
            if (commType.equals(co.toString())) {
                cd.setCommodityClass(co);
                break;
            }
        }

        // Set Key
        String key = elem.getAttribute(KEY);
        if (key != null && !key.isEmpty()) {
            cd.setKey(key);
        }

        // Set Capacity
        try {
            String capacity = elem.getAttribute(ATTR_CAPACITY);
            if (capacity != "")
                cd.setCapacity(Float.parseFloat(capacity));
        }
        catch (Exception e) {
            logger.error(LOGPREFIX + "Capacity parsing error: ", e);
        }

        // Set Used
        try {
            String used = elem.getAttribute(ATTR_USED);
            if (used != "")
                cd.setUsed(Float.parseFloat(used));
        }
        catch (Exception e) {
            logger.error(LOGPREFIX + "Usage parsing error: ", e);
        }
        return cd;
    }

    /**
     * Discover Target
     * 
     * @param accountValues Map representing the values for the fields in the AccountDefintion
     *            required for discovering the target
     * @return Entities discovered by the probe as a set of {@link EntityDTO}
     * 
     */
    @Override
    protected Set<EntityDTO> discoverTarget(Map<String, String> accountValues) {
        logger.info(LOGPREFIX + "Discover Target");
        Set<EntityDTO> entityDTOSet = new HashSet<EntityDTO>();

        // Get the XML topology file path.
        String fileName = accountValues.get(AccountDefinitionEntry.NAME_OR_ADDRESS_FIELD);

        logger.info(LOGPREFIX + "Start parsing the file: " + fileName);

        EntityDTO tempDc = null;
        List<EntityDTO> pmList = new ArrayList<EntityDTO>();
        List<EntityDTO> stList = new ArrayList<EntityDTO>();

        try {
            // Create a Document Object for the XML topology file.
            DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
            logger.info(LOGPREFIX + "Started loading discovery file " + fileName);
            InputStream inputStream = this.getClass().getClassLoader()
                            .getResourceAsStream(fileName);
            if (inputStream == null) {
                logger.error(LOGPREFIX + "Cannot find the file: " + fileName);
                return entityDTOSet;
            }
            Document doc = docBuilder.parse(inputStream);
            doc.getDocumentElement().normalize();
            logger.info(LOGPREFIX + "Ended loading discovery file " + fileName);

            // Find the node list for the Market.
            NodeList mktNodeList = doc.getElementsByTagName(TAG_MARKET);
            int mktNodeListLen = mktNodeList.getLength();
            for (int i = 0; i < mktNodeListLen; i++) {
                Node mktNode = mktNodeList.item(i);
                if (mktNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                logger.info(LOGPREFIX + "File name received for loading: " + fileName);


                Element mktElem = (Element)mktNode;
                if (!mktElem.getAttribute(ATTR_MAIN_MARKET).equals("true")) { // Target on the main Market.
                    continue;
                }

                // Parse the service entities (SE).
                NodeList entityNodeList = mktElem.getElementsByTagName(TAG_ENTITIES);
                int entityNodeListLen = entityNodeList.getLength();
                for (int j = 0; j < entityNodeListLen; j++) {
                    Node entityNode = entityNodeList.item(j);
                    if (entityNode.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }

                    // Generate the entity DTO for the SE.
                    Element entityElem = (Element)entityNode;
                    EntityDTO ed = generateEntityDTO(entityElem);

                    if (ed.getEntity().equals(Entity.ActionManager))
                        continue; // Skip the ActionManager SE's
                    if (ed.getEntity().equals(Entity.Application))
                        continue; // Skip the Application SE's

                    entityDTOSet.add(ed);

                    // Store PM, storage, and data center for further setup of entity relationships.
                    if (ed.getEntity().equals(ModelEnum.Entity.PhysicalMachine)) {
                        pmList.add(ed);
                    }
                    if (ed.getEntity().equals(ModelEnum.Entity.Storage)) {
                        stList.add(ed);
                    }
                    if (ed.getEntity().equals(ModelEnum.Entity.DataCenter)) {
                        tempDc = ed;
                    }
                }
            }
        }
        catch (Exception e) {
            logger.error(LOGPREFIX + "SE ParseError: ", e);
        }

        // Set provider information for commodities bought for each SE.
        // For each commodity bought, find the corresponding commodity sold from the consumes uuid.
        // Then, find the provider's uuid from the consumes uuid.
        for (EntityDTO ed : entityDTOSet) {
            Map<String, List<CommodityDTO>> commBoughtMap = new HashMap<String, List<CommodityDTO>>();
            Map<String, CommodityDTO> consumesToCommBoughtMap = edToConsCommBoughtMap.get(ed);
            for (Map.Entry<String, CommodityDTO> entry : consumesToCommBoughtMap.entrySet()) {
                String commUuid = entry.getKey();
                CommodityDTO commBought = entry.getValue();
                String provUuid = commToSeMap.get(commUuid);
                EntityDTO.putCommBoughtInMap(provUuid, commBought, commBoughtMap);
            }
            ed.setBoughtMap(commBoughtMap);
        }

        // Set up the relationship that each PM is underlying all storages.
        for (EntityDTO pm : pmList) {
            for (EntityDTO st : stList) {
                pm.getUnderlying().add(st.getId());
            }
        }

        // Set up the relationship that the data center consists of all PMs.
        for (EntityDTO pm : pmList) {
            tempDc.getConsistsOf().add(pm.getId());
        }

        return entityDTOSet;
    }

    /**
     * Class of the pair of provider and commodity bought list
     */
    public class CommBoughtPair {
        public final Provider provider;
        public final List<CommodityDTO> commList;

        public CommBoughtPair(Provider provider, List<CommodityDTO> commList) {
            this.provider = provider;
            this.commList = commList;
        }
    }

    public class TemplateCommBoughtPair {
        public final Provider provider;
        public final List<TemplateCommodity> commList;

        public TemplateCommBoughtPair(Provider provider, List<TemplateCommodity> commList) {
            this.provider = provider;
            this.commList = commList;
        }
    }

    /**
     * Generate a template DTO with TemplateType = Base and templatePriority = 0.
     * 
     * @param entityType Entity type
     * @param commSoldList Commodity sold list
     * @param commBoughtPair Pair of provider and commodity bought list
     * @return A template DTO
     */
    protected TemplateDTO generateTemplateDTO(Entity entityType,
                                              List<TemplateCommodity> commSoldList,
                                              TemplateCommBoughtPair... commBoughtPair) {
        Map<Provider, List<TemplateCommodity>> commBoughtMap = new HashMap<Provider, List<TemplateCommodity>>();
        TemplateDTO tempDTO = new TemplateDTO(entityType, commSoldList, commBoughtMap,
                                              TemplateType.Base, 0);

        int commBoughtPairLen = commBoughtPair.length;
        for (int i = 0; i < commBoughtPairLen; i++) {
            tempDTO.getBought().put(commBoughtPair[i].provider, commBoughtPair[i].commList);
        }
        return tempDTO;
    }

    /**
     * Generate a commodity DTO with used = 1f, and capacity = 100f and key = "foo" if it is in
     * commWithKeySet; otherwise, key is set to null.
     * 
     * @param commodity Commodity type
     * @return A commodity DTO.
     */
    private CommodityDTO getCommDTO(Commodity commodity) {
        if (commWithKeySet.contains(commodity)) {
            return new CommodityDTO(commodity, "foo", 1f, 100f);
        } else {
            return new CommodityDTO(commodity, null, 1f, 100f);
        }
    }

    /**
     * Generate a TemplateCommodity with and key = "foo" if it is in commWithKeySet; otherwise, key
     * is set to null.
     * 
     * @param commodity Commodity type
     * @return A TemplateCommodity
     */
    private TemplateCommodity getTemplateCommDTO(Commodity commodity) {
        if (commWithKeySet.contains(commodity)) {
            return new TemplateCommodity(commodity, "foo");
        } else {
            return new TemplateCommodity(commodity, null);
        }
    }

    /**
     * Generate an commodity DTO list.
     * 
     * @param commodities Commodity types
     * @return A commodity DTO list
     */
    @SuppressWarnings("unused")
    private List<CommodityDTO> getCommList(Commodity... commodities) {
        List<CommodityDTO> commSold = Lists.newArrayList();
        int commLen = commodities.length;
        for (int i = 0; i < commLen; i++) {
            commSold.add(getCommDTO(commodities[i]));
        }
        return commSold;
    }

    /**
     * Generate an TemplateCommodity list.
     * 
     * @param commodities Commodity types
     * @return A commodity list
     */
    private List<TemplateCommodity> getTemplateCommList(Commodity... commodities) {
        List<TemplateCommodity> commSold = Lists.newArrayList();
        int commLen = commodities.length;
        for (int i = 0; i < commLen; i++) {
            commSold.add(getTemplateCommDTO(commodities[i]));
        }
        return commSold;
    }

    /**
     * Get the supply chain for this probe.
     * 
     * Buying / Selling relationship between service entities: Data centers sell commodities to
     * hosts. Hosts sell commodities to virtual machines. A disk array sells commodities to
     * storages. Storages sell commodities to physical and virtual machines. Virtual machines sell
     * commodities to applications.
     * 
     * @return A set of template DTOs for this probe.
     */
    @Override
    protected Set<TemplateDTO> getSupplyChainDefinition() {
        logger.info(LOGPREFIX + "Get supply chain");

        Set<TemplateDTO> templateDTOSet = Sets.newHashSet();

        // Data center DTO
        List<TemplateCommodity> dcCommSold = getTemplateCommList(Commodity.Power, Commodity.Space,
                                                                 Commodity.Cooling);
        templateDTOSet.add(generateTemplateDTO(Entity.DataCenter, dcCommSold));

        // Network DTO
        templateDTOSet.add(generateTemplateDTO(Entity.Network, null));
        templateDTOSet.add(generateTemplateDTO(Entity.DistributedVirtualPortgroup, null));

        // DiskArray DTO
        List<TemplateCommodity> daCommSold = getTemplateCommList(Commodity.Extent,
                                                                 Commodity.StorageAccess,
                                                                 Commodity.StorageLatency);
        templateDTOSet.add(generateTemplateDTO(Entity.DiskArray, daCommSold));

        // Storage DTO
        List<TemplateCommodity> stCommSold = getTemplateCommList(Commodity.StorageAmount,
                                                                 Commodity.StorageProvisioned,
                                                                 Commodity.StorageAccess,
                                                                 Commodity.StorageLatency);
        Provider stProvDa = new Provider(Entity.DiskArray, ProviderType.HOSTING, 1, 1);
        List<TemplateCommodity> stCommBoughtFromDa = getTemplateCommList(Commodity.Extent,
                                                                         Commodity.StorageAccess,
                                                                         Commodity.StorageLatency);

        templateDTOSet.add(generateTemplateDTO(Entity.Storage, stCommSold,
                                               new TemplateCommBoughtPair(stProvDa,
                                                                          stCommBoughtFromDa)));

        // PM DTO
        List<TemplateCommodity> pmCommSold = getTemplateCommList(Commodity.CPU, Commodity.Mem,
                                                                 Commodity.IOThroughput,
                                                                 Commodity.ClusterCommodity,
                                                                 Commodity.NetThroughput,
                                                                 Commodity.DatastoreCommodity,
                                                                 Commodity.Q1VCPU,
                                                                 Commodity.DataCenterCommodity);

        Provider pmProvDc = new Provider(Entity.DataCenter, ProviderType.HOSTING, 1, 1);
        List<TemplateCommodity> pmCommBoughtFromDc = getTemplateCommList(Commodity.Power,
                                                                         Commodity.Space,
                                                                         Commodity.Cooling);
        Provider pmProvSt = new Provider(Entity.Storage, ProviderType.LAYEREDOVER,
                                         Integer.MAX_VALUE, 0);
        List<TemplateCommodity> pmCommBoughtFromSt = getTemplateCommList(Commodity.StorageAccess,
                                                                         Commodity.StorageLatency);

        templateDTOSet.add(generateTemplateDTO(Entity.PhysicalMachine, pmCommSold,
                                               new TemplateCommBoughtPair(pmProvDc,
                                                                          pmCommBoughtFromDc),
                                               new TemplateCommBoughtPair(pmProvSt,
                                                                          pmCommBoughtFromSt)));

        // VM DTO
        List<TemplateCommodity> vmCommSold = getTemplateCommList(Commodity.VCPU, Commodity.VMem,
                                                                 Commodity.ApplicationCommodity);
        List<TemplateCommodity> vmCommBoughtFromPm = getTemplateCommList(Commodity.CPU,
                                                                         Commodity.Mem);
        List<TemplateCommodity> vmCommBoughtFromSt = getTemplateCommList(Commodity.StorageAmount,
                                                                         Commodity.StorageProvisioned);
        Provider vmProvPm = new Provider(Entity.PhysicalMachine, ProviderType.HOSTING, 1, 1);
        Provider vmProvSt = new Provider(Entity.Storage, ProviderType.LAYEREDOVER,
                                         Integer.MAX_VALUE, 0);

        templateDTOSet.add(generateTemplateDTO(Entity.VirtualMachine, vmCommSold,
                                               new TemplateCommBoughtPair(vmProvPm,
                                                                          vmCommBoughtFromPm),
                                               new TemplateCommBoughtPair(vmProvSt,
                                                                          vmCommBoughtFromSt)));

        // Application DTO
        List<TemplateCommodity> appCommSold = getTemplateCommList();
        List<TemplateCommodity> appCommBoughtFromVm = getTemplateCommList(Commodity.VCPU,
                                                                          Commodity.VMem,
                                                                          Commodity.ApplicationCommodity);
        Provider appProvVm = new Provider(Entity.VirtualMachine, ProviderType.HOSTING, 1, 1);

        templateDTOSet.add(generateTemplateDTO(Entity.Application, appCommSold,
                                               new TemplateCommBoughtPair(appProvVm,
                                                                          appCommBoughtFromVm)));

        return templateDTOSet;
    }

    /**
     * Return the fields and their meta data required by the probe to validate and discover the
     * targets.
     * 
     * @return Account Definition object
     */
    @Override
    protected Map<String, AccountDefinitionEntry> getAccountDefinitionEntryMap() {
        logger.info(LOGPREFIX + "Get account definition");
        /*
         * No Account Definition entries for this probe. 
         * This is because the user is always presented 
         * with the 'nameOrAddress' field which denotes the path of the file
         * that will be parsed for this probe.
         */
        ImmutableMap<String, AccountDefinitionEntry> accountDefinitionEntryMap = ImmutableMap
                        .of(AccountDefinitionEntry.TARGET_IDENTIFIER,
                            new AccountDefinitionEntry(AccountDefinitionEntry.TARGET_IDENTIFIER,
                                                       "File Name",
                                                       "unique identifier of the target",
                                                       AccountDefinitionEntryType.Mandatory, ".*"));
        // TODO(tian): add File name field which is also used as TargetID
        return accountDefinitionEntryMap;
    }

    /**
     * Validate Target
     * 
     * @param accountValues Map representing the values for the fields in the AccountDefintion
     *            required for validating the target
     * @return TargetValidationResponse
     */
    @Override
    protected TargetValidationResponse validateTarget(Map<String, String> accountValues) {
        logger.info(LOGPREFIX + "Validate Target");
        String fileName = accountValues.get(AccountDefinitionEntry.NAME_OR_ADDRESS_FIELD);
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(fileName);
        TargetValidationResponse validationResponse = new TargetValidationResponse();
        if (inputStream != null) {
            validationResponse.targetValidationStatus = ResponseCode.SUCCESS;
        } else {
            validationResponse.targetValidationStatus = ResponseCode.FAIL;
            validationResponse.targetValidationExplanation = "File " + fileName
                                                             + " does not exist.";
        }
        return validationResponse;
    }
}
