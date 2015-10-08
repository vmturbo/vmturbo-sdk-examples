package com.vmturbo.sdk.examples.fileProbe;

import java.io.IOException;
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
import com.google.common.collect.Sets;

import com.vmturbo.platform.common.dto.CommodityDTO;
import com.vmturbo.platform.common.dto.ErrorDTO;
import com.vmturbo.platform.common.dto.ErrorSeverity;
import com.vmturbo.platform.common.dto.ModelEnum;
import com.vmturbo.platform.common.dto.ModelEnum.Commodity;
import com.vmturbo.platform.common.dto.ModelEnum.Entity;
import com.vmturbo.platform.common.dto.ModelEnum.TemplateType;
import com.vmturbo.platform.sdk.common.DTO.EntityDTO;
import com.vmturbo.platform.sdk.common.DTO.Provider;
import com.vmturbo.platform.sdk.common.DTO.ProviderType;
import com.vmturbo.platform.sdk.common.DTO.TemplateDTO;
import com.vmturbo.platform.sdk.common.DTO.TemplateDTO.TemplateCommodity;
import com.vmturbo.platform.sdk.common.supplychain.EntityBuilder;
import com.vmturbo.platform.sdk.common.supplychain.EntityLink;
import com.vmturbo.platform.sdk.common.supplychain.SupplyChainBuilder;
import com.vmturbo.platform.sdk.common.supplychain.SupplyChainLinkBuilder;
import com.vmturbo.platform.sdk.common.supplychain.SupplyChainNodeBuilder;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntry;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntryType;
import com.vmturbo.platform.sdk.common.util.TargetDiscoveryResponse;
import com.vmturbo.platform.sdk.common.util.TargetValidationResponse;
import com.vmturbo.platform.sdk.probe.IProbe;

/**
 * A sample implementation of the file probe. This probe reads data from an XML topology file and
 * generates entity DTOs for the target.
 */
public class FileProbe implements IProbe {

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

    private final Logger logger = Logger.getLogger(getClass());

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
        //EntityDTO ed = new EntityDTO(null, null, null, null, null);
        EntityBuilder eb = new EntityBuilder();
        eb.entity(Entity.Unknown, elem.getAttribute(ATTR_UUID));

        String entityType = elem.getAttribute(ATTR_ENTITY_TYPE);
        if (entityType.startsWith(ABSTRACTION)) {
            entityType = entityType.replaceFirst(ABSTRACTION, "");
        } else if (entityType.startsWith(NETWORKING)) {
            entityType = entityType.replaceFirst(NETWORKING, "");
        }

        for (Entity en : Entity.values()) {
            if (entityType.equals(en.toString())) {
                eb.entity(en, elem.getAttribute(ATTR_UUID));
                break;
            }
        }

        // If no "dispalyName" tag found, use "name" tag
        String name = elem.getAttribute(ATTR_DISP_NAME);
        if (name.equals("")) {
            name = elem.getAttribute(ATTR_NAME);
        }
        eb.displayName(name);

        // Construct the commodity objects from the topology file
        NodeList commNodeList = elem.getElementsByTagName(TAG_COMM);
        int commNodeListLen = commNodeList.getLength();
        for (int k = 0; k < commNodeListLen; k++) {
            Node commNode = commNodeList.item(k);
            if (commNode.getNodeType() == Node.ELEMENT_NODE) {
                // Find commodity type
                String commType = ((Element)commNode).getAttribute(ATTR_ENTITY_TYPE).replaceFirst(ABSTRACTION, "");
                Commodity commDTOType = Commodity.Unknown;
                for (Commodity co : Commodity.values()) {
                    if (commType.equals(co.toString())) {
                        commDTOType = co;
                        break;
                    }
                }

                // Set Key
                String key = ((Element)commNode).getAttribute(KEY);
                if (key != null && !key.isEmpty()) {
                    eb.sells(commDTOType, key);
                } else if (commWithKeySet.contains(commDTOType)) {
                    eb.sells(commDTOType, "foo");
                }
                else {
                    eb.sells(commDTOType);
                }

                // Set Capacity
                try {
                    String capacity = ((Element)commNode).getAttribute(ATTR_CAPACITY);
                    if (capacity != "") {
                        eb.capacity(Float.parseFloat(capacity));
                    } else if (commWithKeySet.contains(commDTOType)) {
                        eb.capacity(100f);
                    }
                }
                catch (Exception e) {
                    logger.error("Capacity parsing error: ", e);
                }

                // Set Used

                try {
                    String used = ((Element)commNode).getAttribute(ATTR_USED);
                    if (used != "") {
                        eb.used(Float.parseFloat(used));
                    } else if (commWithKeySet.contains(commDTOType)) {
                        eb.used(1f);
                    }
                }
                catch (Exception e) {
                    logger.error("Usage parsing error: ", e);
                }

                String uuid = ((Element)commNode).getAttribute(ATTR_UUID);
                commToSeMap.put(uuid, elem.getAttribute(ATTR_UUID));
            }
        }

        // Construct the commodities bought objects from the topology file
        Map<String, CommodityDTO> consumesToCommMap = new HashMap<String, CommodityDTO>();
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

        EntityDTO ed = eb.configure();
        edToConsCommBoughtMap.put(ed, consumesToCommMap);
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
        } else if (commWithKeySet.contains(cd.getCommodity())) {
            cd.setKey("foo");
        }

        // Set Capacity
        try {
            String capacity = elem.getAttribute(ATTR_CAPACITY);
            if (capacity != "") {
                cd.setCapacity(Float.parseFloat(capacity));
            } else if (commWithKeySet.contains(cd.getCommodity())) {
                cd.setCapacity(100f);
            }
        }
        catch (Exception e) {
            logger.error("Capacity parsing error: ", e);
        }

        // Set Used
        try {
            String used = elem.getAttribute(ATTR_USED);
            if (used != "") {
                cd.setUsed(Float.parseFloat(used));
            } else if (commWithKeySet.contains(cd.getCommodity())) {
                cd.setUsed(1f);
            }
        }
        catch (Exception e) {
            logger.error("Usage parsing error: ", e);
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
    public TargetDiscoveryResponse discoverTarget(Map<String, String> accountValues) {
        logger.info("Discover Target");
        final TargetDiscoveryResponse response = new TargetDiscoveryResponse();
        Set<EntityDTO> entityDTOSet = new HashSet<EntityDTO>();

        // Get the XML topology file path.
        String fileName = accountValues.get(AccountDefinitionEntry.NAME_OR_ADDRESS_FIELD);

        logger.info("Start parsing the file: " + fileName);

        EntityDTO tempDc = null;
        List<EntityDTO> pmList = new ArrayList<EntityDTO>();
        List<EntityDTO> stList = new ArrayList<EntityDTO>();

        try {
            // Create a Document Object for the XML topology file.
            DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
            logger.info("Started loading discovery file " + fileName);
            InputStream inputStream = this.getClass().getClassLoader()
                            .getResourceAsStream(fileName);
            if (inputStream == null) {
                final String message = "Cannot find the file: " + fileName;
                logger.error(message);
                response.getErrors().add(new ErrorDTO(ErrorSeverity.CRITICAL, message));
                return response;
            }
            Document doc = docBuilder.parse(inputStream);
            doc.getDocumentElement().normalize();
            logger.info("Ended loading discovery file " + fileName);

            // Find the node list for the Market.
            NodeList mktNodeList = doc.getElementsByTagName(TAG_MARKET);
            int mktNodeListLen = mktNodeList.getLength();
            for (int i = 0; i < mktNodeListLen; i++) {
                Node mktNode = mktNodeList.item(i);
                if (mktNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                logger.info("File name received for loading: " + fileName);

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

                    if (ed.getEntity().equals(Entity.ActionManager)) {
                        continue; // Skip the ActionManager SE's
                    }
                    if (ed.getEntity().equals(Entity.Application)) {
                        continue; // Skip the Application SE's
                    }

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
            logger.error("SE ParseError: ", e);
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
                ed.addCommodityBought(provUuid, commBought);
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

        response.setEntities(entityDTOSet);
        return response;
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
    public Set<TemplateDTO> getSupplyChainDefinition() {
        logger.info("Get supply chain");

        String key = "foo";
        SupplyChainBuilder scb = new SupplyChainBuilder();

        // Setup Supply Chain Nodes
        // Application Node and commodity list
        SupplyChainNodeBuilder appNode = new SupplyChainNodeBuilder();
        appNode.entity(Entity.Application);

        // VM Node and commodity list
        SupplyChainNodeBuilder vmNode = new SupplyChainNodeBuilder();
        vmNode.entity(Entity.VirtualMachine);
        vmNode.selling(Commodity.VCPU)
              .selling(Commodity.VMem)
              .selling(Commodity.ApplicationCommodity, key);

        // PM Node and commodity list
        SupplyChainNodeBuilder pmNode = new SupplyChainNodeBuilder();
        pmNode.entity(Entity.PhysicalMachine);
        pmNode.selling(Commodity.CPU)
              .selling(Commodity.Mem)
              .selling(Commodity.IOThroughput)
              .selling(Commodity.NetThroughput)
              .selling(Commodity.Q1VCPU)
              .selling(Commodity.DatastoreCommodity, key)
              .selling(Commodity.DataCenterCommodity, key)
              .selling(Commodity.ClusterCommodity, key);

        // Data center Node and commodity list
        SupplyChainNodeBuilder dcNode = new SupplyChainNodeBuilder();
        dcNode.entity(Entity.DataCenter);
        dcNode.selling(Commodity.Space)
              .selling(Commodity.Power)
              .selling(Commodity.Cooling);

        // Network Node
        SupplyChainNodeBuilder ntNode = new SupplyChainNodeBuilder();
        ntNode.entity(Entity.Network);

        // Distributed Virtual Port Group Node
        SupplyChainNodeBuilder dvpNode = new SupplyChainNodeBuilder();
        dvpNode.entity(Entity.DistributedVirtualPortgroup);

        // Storge Node and commodity list
        SupplyChainNodeBuilder stNode = new SupplyChainNodeBuilder();
        stNode.entity(Entity.Storage);
        stNode.selling(Commodity.StorageAmount)
              .selling(Commodity.StorageAccess)
              .selling(Commodity.StorageProvisioned)
              .selling(Commodity.StorageLatency);

        // DiskArray Node and commodity list
        SupplyChainNodeBuilder daNode = new SupplyChainNodeBuilder();
        daNode.entity(Entity.DiskArray);
        daNode.selling(Commodity.StorageAccess)
              .selling(Commodity.StorageLatency)
              .selling(Commodity.Extent, key);


        // Setup Supply Chain Links
        // Add Application node into supply chain
        scb.top(appNode);

        // Add VM node into supply chain
        SupplyChainLinkBuilder sclb = new SupplyChainLinkBuilder();
        sclb.link(Entity.Application, Entity.VirtualMachine, ProviderType.HOSTING);
        sclb.commodity(Commodity.VCPU)
            .commodity(Commodity.VMem)
            .commodity(Commodity.ApplicationCommodity);
        EntityLink linkVMToApp = sclb.build();
        scb.connectsTo(vmNode, linkVMToApp);
        scb.entity(vmNode);

        // Add PM node into supply chain
        sclb = new SupplyChainLinkBuilder();
        sclb.link(Entity.VirtualMachine, Entity.PhysicalMachine, ProviderType.HOSTING);
        sclb.commodity(Commodity.CPU)
            .commodity(Commodity.Mem);
        EntityLink linkPMToVM = sclb.build();
        scb.connectsTo(pmNode, linkPMToVM);
        scb.entity(pmNode);

        // Add Datacenter into supply chain
        sclb = new SupplyChainLinkBuilder();
        sclb.link(Entity.PhysicalMachine, Entity.DataCenter, ProviderType.HOSTING);
        sclb.commodity(Commodity.Power)
            .commodity(Commodity.Space)
            .commodity(Commodity.Cooling);
        EntityLink linkDCToPM = sclb.build();
        scb.connectsTo(dcNode, linkDCToPM);
        scb.entity(dcNode);

        // Add Network node into supply chain
        scb.entity(ntNode);

        // Add Distributed Virtual Port Group node into supply chain
        scb.entity(dvpNode);

        // Add Storage node into supply chain
        // Setup connection between ST and VM
        sclb = new SupplyChainLinkBuilder();
        sclb.link(Entity.VirtualMachine, Entity.Storage, ProviderType.LAYEREDOVER);
        sclb.commodity(Commodity.StorageAmount)
            .commodity(Commodity.StorageProvisioned);
        EntityLink linkSTToVM = sclb.build();
        // Setup VM node which will be connected with ST node in Supply Chain
        scb.entity(vmNode);
        scb.connectsTo(stNode, linkSTToVM);

        // Setup connection between ST and PM
        sclb = new SupplyChainLinkBuilder();
        sclb.link(Entity.PhysicalMachine, Entity.Storage, ProviderType.LAYEREDOVER);
        sclb.commodity(Commodity.StorageAccess)
            .commodity(Commodity.StorageLatency);
        EntityLink linkSTToPM = sclb.build();
        // Setup pm node which will be connected with storage node in Supply Chain
        scb.entity(pmNode);
        scb.connectsTo(stNode, linkSTToPM);
        // Setup ST node into Supply Chain Builder
        scb.entity(stNode);

        // Add DiskArray node into supply chain
        sclb = new SupplyChainLinkBuilder();
        sclb.link(Entity.Storage, Entity.DiskArray, ProviderType.HOSTING);
        sclb.commodity(Commodity.StorageAccess)
            .commodity(Commodity.StorageLatency);
        EntityLink linkDAToST = sclb.build();
        scb.connectsTo(daNode, linkDAToST);
        scb.entity(daNode);

        return scb.configure();
    }

    /**
     * Return the fields and their meta data required by the probe to validate and discover the
     * targets.
     *
     * @return Account Definition object
     */
    @Override
    public Map<String, AccountDefinitionEntry> getAccountDefinitionEntryMap() {
        logger.info("Get account definition");
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
    public TargetValidationResponse validateTarget(Map<String, String> accountValues) {
        logger.info("Validate Target");
        String fileName = accountValues.get(AccountDefinitionEntry.NAME_OR_ADDRESS_FIELD);
        final InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException ex) {
                logger.error("Unable to close file " + fileName, ex);
            }
            return TargetValidationResponse.createOkResponse();
        } else {
            return TargetValidationResponse.createFailedResponse(new ErrorDTO(
                            ErrorSeverity.CRITICAL, "File " + fileName));
        }

    }
}
