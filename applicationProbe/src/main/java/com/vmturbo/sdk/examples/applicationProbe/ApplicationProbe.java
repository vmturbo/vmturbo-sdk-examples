package com.vmturbo.sdk.examples.applicationProbe;

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
import com.vmturbo.platform.sdk.common.supplychain.ExternalEntityLink;
import com.vmturbo.platform.sdk.common.supplychain.ExternalEntityLinkDef;
import com.vmturbo.platform.sdk.common.supplychain.ExternalLinkBuilder;
import com.vmturbo.platform.sdk.common.supplychain.SupplyChainBuilder;
import com.vmturbo.platform.sdk.common.supplychain.SupplyChainConstants;
import com.vmturbo.platform.sdk.common.supplychain.SupplyChainNodeBuilder;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntry;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntryType;
import com.vmturbo.platform.sdk.common.util.ResponseCode;
import com.vmturbo.platform.sdk.common.util.TargetValidationResponse;
import com.vmturbo.platform.sdk.probe.AbstractProbe;
import com.vmturbo.platform.sdk.probe.builder.ApplicationBuilder;
/**
 * This is a probe that instantiates Applications found in the target.
 * The probe type configured for this probe is "CUSTOM".
 *
 * The Application objects created by this probe are configured with IP address of the Virtual
 * Machine(VM) that it is hosted on. These IP addresses are used to connect with the VMs discovered
 * by Hypervisor targets.
 *
 * @author haoyuanwang
 *
 */
public class ApplicationProbe extends AbstractProbe {

    String LOGPREFIX="";
    final String APP1_ID = "app1-id";
    final String APP1_NAME = "app1";
    final String App1_IP_VAL = "10.0.0.0";

    private static final Logger logger = Logger
                    .getLogger("com.vmturbo.platform.container.mediation");

    @Override
    /**
     * Get the supply chain for the Application probe.
     *The supply chain has Application that will buy VCPU and VMem commodities from VM. The VM is
     *not discovered by the supply chain but we send the meta data for connecting in the supply
     *chain.
     *
     * @return A set of template DTOs for each entity type created by this supply chain.
     */
    protected Set<TemplateDTO> getSupplyChainDefinition() {
        logger.info(LOGPREFIX + "Get supply chain");

        // Create supply chain builder
        SupplyChainBuilder appScb=new SupplyChainBuilder();

        // Create the Application node
        SupplyChainNodeBuilder appNode = new SupplyChainNodeBuilder()
        .entity(Entity.Application);

        // Link from Application to VM
        ExternalLinkBuilder vmb = new ExternalLinkBuilder()
        .link(Entity.Application,Entity.VirtualMachine,ProviderType.HOSTING);
        vmb.commodity(Commodity.VCPU)
        .commodity(Commodity.VMem);
        vmb.probeEntityPropertyDef(SupplyChainConstants.IP_ADDRESS, App1_IP_VAL)
        .externalEntityPropertyDef(ExternalEntityLinkDef.VM_IP);
        ExternalEntityLink vm = vmb.build();

        // Create the supply chain
        appScb.top(appNode)
            .connectsTo(vm);

        //Generate TemplateDTOs
        return appScb.configure();
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
        // get unique target identifier defined by user in UI
        String targetID = accountDefinitionMap.get(AccountDefinitionEntry.TARGET_IDENTIFIER);

        // get properties from the properties file which has the same name as target identifier
        // if no such file, use the default value
        Properties props = getPropValues(targetID);

        String appId = "app-";
        // build application id using the target ID
        appId = appId + accountDefinitionMap.get(AccountDefinitionEntry.TARGET_IDENTIFIER);
        String ipAddr = App1_IP_VAL;
        String appType = "GuestLoad";
        if (props != null){
            // get ip address for the application
            if(props.containsKey("IPAddress")) {
                ipAddr = props.getProperty("IPAddress");
                ipAddr = ipAddr.trim();
            }

            // get application type
            if(props.containsKey("Type")) {
                appType = props.getProperty("Type");
                appType = appType.trim();
            }
        }

        ApplicationBuilder ab = new ApplicationBuilder(appId)
        .appType(appType)
        .displayName(appId)
        .ip(ipAddr);
        EntityDTO app = ab.configure();
        return Sets.newHashSet(app);
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
        if(targetID != null) {
            targetID = targetID.trim();
        }
        Properties prop = new Properties();

        // create name for the specific Properties file
        String propFileName = targetID + ".properties";

        logger.info(LOGPREFIX + "Start loading properties file " + propFileName);

        try {
            // setup file path for the Properties file
            File catalinaBase = new File( System.getProperty( "catalina.base" ) ).getAbsoluteFile();
            File file = new File( catalinaBase, "webapps/MediationContainer/probe-jars/" + propFileName);

            if(file.exists()) {
                // load properties file in a stream
                InputStream inputStream = new FileInputStream(file);
                prop.load(inputStream);
                return prop;
            }
        } catch (Exception e) {
            logger.error("Exception in ApplicationProbe::getPropValues: " + e);
        }

        // Use default properties file if no specific properties file found
        return getDefaultPropValues();
    }

    /**
     * Load a default Properties file.
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

    /**
     * Validates the target using the given credential.
     *
     * @param accountValues     Map representing the values for the fields in the AccountDefintion
     *                          required for validating the target
     *
     * @return                  {@link TargetValidationResponse}
     */
    @Override
    protected TargetValidationResponse validateTarget(Map<String, String> accountDefinitionMap) {
        logger.info(LOGPREFIX + "validate target");
        TargetValidationResponse validationResponse = new TargetValidationResponse();
        validationResponse.targetValidationStatus = ResponseCode.SUCCESS;
        validationResponse.targetValidationExplanation = "Sample Application Probe Validated";
        return validationResponse;
    }
}
