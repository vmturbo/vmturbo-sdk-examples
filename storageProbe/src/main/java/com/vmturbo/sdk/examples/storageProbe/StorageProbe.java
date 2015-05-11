package com.vmturbo.sdk.examples.storageProbe;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.vmturbo.platform.common.dto.ModelEnum.Commodity;
import com.vmturbo.platform.common.dto.ModelEnum.Entity;
import com.vmturbo.platform.sdk.common.DTO.EntityDTO;
import com.vmturbo.platform.sdk.common.DTO.ProviderType;
import com.vmturbo.platform.sdk.common.DTO.TemplateDTO;
import com.vmturbo.platform.sdk.common.supplychain.EntityBuilder;
import com.vmturbo.platform.sdk.common.supplychain.EntityLink;
import com.vmturbo.platform.sdk.common.supplychain.ExternalEntityLink;
import com.vmturbo.platform.sdk.common.supplychain.ExternalEntityLinkDef;
import com.vmturbo.platform.sdk.common.supplychain.ExternalLinkBuilder;
import com.vmturbo.platform.sdk.common.supplychain.SupplyChainBuilder;
import com.vmturbo.platform.sdk.common.supplychain.SupplyChainConstants;
import com.vmturbo.platform.sdk.common.supplychain.SupplyChainLinkBuilder;
import com.vmturbo.platform.sdk.common.supplychain.SupplyChainNodeBuilder;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntry;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntryType;
import com.vmturbo.platform.sdk.common.util.ResponseCode;
import com.vmturbo.platform.sdk.common.util.TargetValidationResponse;
import com.vmturbo.platform.sdk.probe.AbstractProbe;
import com.vmturbo.platform.sdk.probe.builder.DiskArrayBuilder;
/**
 * This is a probe that instantiates DiskArray found in the target.
 * The probe type configured for this probe is "CUSTOM".
 *
 * The DiskArray objects created by this probe are configured with LunUUID of the Storage Controller
 *  that it is hosted on. These LunUUID are used to connect with the Storage Controller.
 *
 * @author haoyuanwang
 *
 */
public class StorageProbe extends AbstractProbe {
    private static final Logger logger = Logger
                    .getLogger("com.vmturbo.platform.container.mediation");
    String LOGPREFIX="";
    final String DA1_ID = "da1-id";
    final String DA1_NAME = "da1-name";
    final String DA1_PATH_VAL = "DA-PATH-VAL";
    final String SC_ID = "SC-ID-VAL";
    final String DA_LUN_VAL = "00000000";
    /**
     * Get the supply chain for the Storage probe.
     *The supply chain has DiskArray that will buy commodities from StorageControler and sell
     *commodities to Storage. The Storage is not discovered by the supply chain but we send the
     *meta data for connecting in the supply chain.
     *
     * @return A set of template DTOs for each entity type created by this supply chain.
     */
    @Override
    protected Set<TemplateDTO> getSupplyChainDefinition() {
        logger.info(LOGPREFIX + "Get supply chain");
        // Create supply chain builder
        SupplyChainBuilder stScb = new SupplyChainBuilder();

        // Create the Disk Array node
        SupplyChainNodeBuilder daNode = new SupplyChainNodeBuilder()
        .entity(Entity.DiskArray)
        .selling(Commodity.StorageAmount)
        .selling(Commodity.StorageLatency)
        .selling(Commodity.StorageProvisioned)
        .selling(Commodity.Extent);

        // Create the StorageController node
        SupplyChainNodeBuilder scNode = new SupplyChainNodeBuilder();
        scNode.entity(Entity.StorageController)
        .selling(Commodity.StorageAmount)
        .selling(Commodity.CPU);

        // Create the Storage node
        ExternalLinkBuilder stb = new ExternalLinkBuilder();
        stb.link(Entity.Storage, Entity.DiskArray, ProviderType.HOSTING);
        stb.commodity(Commodity.StorageProvisioned)
            .commodity(Commodity.StorageAmount)
            .commodity(Commodity.StorageAccess)
            .commodity(Commodity.StorageLatency)
            .commodity(Commodity.Extent);
        //Set LunUUID to be the property that will be used to stitch DiskArray and Storage
        stb.probeEntityPropertyDef(SupplyChainConstants.STORAGE_ID, "LunUUID")
        .externalEntityPropertyDef(ExternalEntityLinkDef.STORAGE_LUNUUID);
        ExternalEntityLink st=stb.build();

        // Link from DA to SC
        SupplyChainLinkBuilder da2sc = new SupplyChainLinkBuilder()
        .link(Entity.DiskArray, Entity.StorageController, ProviderType.HOSTING)
        .commodity(Commodity.CPU)
        .commodity(Commodity.StorageAmount);
        EntityLink da2scLink = da2sc.build();

        // Top node, connect DA to ST
        stScb.top(daNode);
        stScb.connectsTo(st);
        stScb.connectsTo(scNode, da2scLink);
        stScb.entity(scNode);

        return stScb.configure();
    }

    @Override
    /**
     * Return the fields and their meta data required by the probe to validate and discover the
     * targets.
     *
     * @return Account Definition object
     */
    protected Map<String, AccountDefinitionEntry> getAccountDefinitionEntryMap() {
        logger.info(LOGPREFIX + "Get account definition");
        ImmutableMap<String, AccountDefinitionEntry> accountDefinitionEntryMap = ImmutableMap
                        .of(
                            /*
                             * This mandatory field denotes the instance name of the target.
                             */
                            AccountDefinitionEntry.TARGET_IDENTIFIER,
                            new AccountDefinitionEntry(AccountDefinitionEntry.TARGET_IDENTIFIER,
                                                       "Name", "name of the target",
                                                       AccountDefinitionEntryType.Mandatory, ".*"),
                            /*
                             * This mandatory field denotes the user name required to connect to the target
                             */
                            AccountDefinitionEntry.USERNAME_FIELD,
                            new AccountDefinitionEntry(AccountDefinitionEntry.USERNAME_FIELD,
                                                       "User", "username to login to the target",
                                                       AccountDefinitionEntryType.Mandatory, ".*"),
                            /*
                             * This mandatory field denotes the password required to connect to the target
                             */
                            AccountDefinitionEntry.PASSWORD_FIELD,
                            new AccountDefinitionEntry(AccountDefinitionEntry.PASSWORD_FIELD,
                                                       "Password", "password for the account",
                                                       AccountDefinitionEntryType.Mandatory, ".*"));
        return accountDefinitionEntryMap;
    }

    @Override
    /**
     * Discovers the target, creating an {@link EntityDTO} representation of every object discovered
     * by the probe.
     *
     * @param accountValues     Map representing the values for the fields in the AccountDefintion
     *                          required for discovering the target
     * @return                  Entities discovered by the probe as a set of {@link EntityDTO}
     */
    protected Set<EntityDTO> discoverTarget(Map<String, String> accountDefinitionMap) {
        logger.info(LOGPREFIX + "discover target");
        String dispNamePrefix = "da-";
        // get unique target identifier defined by user in UI
        String targetID = accountDefinitionMap.get(AccountDefinitionEntry.TARGET_IDENTIFIER);

        //set display name based on target identifier
        String dispName = dispNamePrefix + accountDefinitionMap.get(AccountDefinitionEntry.TARGET_IDENTIFIER);

        // get properties from the properties file which has the same name as target identifier
        // if no such file, use the default value
        Properties props = getPropValues(targetID);

        String lunUUIDs = DA_LUN_VAL;
        if(props!=null && props.containsKey("LunUUID")){
            lunUUIDs = props.getProperty("LunUUID");
        }
        lunUUIDs=lunUUIDs.trim();

        // DiskArray entity DTO
        DiskArrayBuilder da = new DiskArrayBuilder(DA1_ID)
        .displayName(DA1_NAME)
        .lunId(lunUUIDs)
        .path(DA1_PATH_VAL)
        // Commodities sold
        .storageAcess(100F)
        .storageAmount(100F)
        .storageProvisioned(100F)
        .storageLatency(100f)
        .storageExtent(100f)
        // Commodities bought, with corresponding provider
        .storageController(SC_ID)
        .storageAmountBought(null, 100f, 1f)
        .cpuBought(null, 100f, 1f);
        EntityDTO dae = da.configure();

        // StorageController entity DTO
        EntityDTO sce = new EntityBuilder()
        .entity(Entity.StorageController, SC_ID)
        .displayName(SC_ID)
        .sells(Commodity.CPU)
        .capacity(100f)
        .sells(Commodity.StorageAmount)
        .capacity(100f)
        .configure();

        return Sets.newHashSet(dae, sce);
    }

    /**
     * Load Properties from the specific Properties file. Name of the Properties file should follow the targetID.
     * For example, for a target with an ID of "exampleTarget", that Properties file should be named as "exampleTarget.properties".
     * The specific Properties file should be allocated under $catalinaBase/webapps/MediationContainer/probe-jars.
     * If such a file is not found, a default Properties file will be loaded and applied.
     * @param targetID: the unique identifier given to the target
     * @return
     */
    public Properties getPropValues(String targetID) {
        if(targetID==null){
            return getDefaultPropValues();
        }

        targetID.trim();
        Properties prop = new Properties();

        // create name of the specific Properties file
        String propFileName = targetID + ".properties";
        logger.info(LOGPREFIX + "Start loading properties file " + propFileName);
        try {
            // setup file path for the Properties file
            File catalinaBase = new File( System.getProperty( "catalina.base" ) ).getAbsoluteFile();
            File file = new File( catalinaBase, "webapps/MediationContainer/probe-jars/" + propFileName);

            if(file.exists()){
                InputStream inputStream = new FileInputStream(file);
                prop.load(inputStream);
                return prop;
            }
        } catch (Exception e) {
            logger.error("Exception in ApplicationProbe::getPropValues: " + e);
        }
        //Use default properties file if no specific properties file found
        return getDefaultPropValues();
    }
    /**
     * Load the default Properties file.
     * @return
     */
    public Properties getDefaultPropValues() {
    Properties prop = new Properties();
    String propFileName = "default.properties";
    logger.info(LOGPREFIX + "Started loading default properties file " + propFileName);
    try {
    InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(propFileName);
    if (inputStream == null) {
        logger.error(LOGPREFIX + "Property file '" + propFileName + "' not found in the classpath");
        return null;
    }
    if (inputStream != null) {
        prop.load(inputStream);
    }
    } catch (Exception ex){
        ex.printStackTrace();
    }
    return prop;
}

    @Override
    /**
     * Validates the target using the given credential.
     *
     * @param accountValues     Map representing the values for the fields in the AccountDefintion
     *                          required for validating the target
     *
     * @return                  {@link TargetValidationResponse}
     */
    protected TargetValidationResponse validateTarget(Map<String, String> accountValues) {
        logger.info(LOGPREFIX + "validate target");
        TargetValidationResponse validationResponse = new TargetValidationResponse();
        validationResponse.targetValidationStatus = ResponseCode.SUCCESS;
        validationResponse.targetValidationExplanation = "Sample Storage Probe Validated";
        return validationResponse;
    }

}
