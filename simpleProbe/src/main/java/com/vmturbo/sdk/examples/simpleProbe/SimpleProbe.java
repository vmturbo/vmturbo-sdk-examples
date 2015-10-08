package com.vmturbo.sdk.examples.simpleProbe;

import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.vmturbo.platform.common.dto.ModelEnum.Commodity;
import com.vmturbo.platform.common.dto.ModelEnum.Entity;
import com.vmturbo.platform.sdk.common.DTO.EntityDTO;
import com.vmturbo.platform.sdk.common.DTO.ProviderType;
import com.vmturbo.platform.sdk.common.DTO.TemplateDTO;
import com.vmturbo.platform.sdk.common.supplychain.EntityLink;
import com.vmturbo.platform.sdk.common.supplychain.SupplyChainBuilder;
import com.vmturbo.platform.sdk.common.supplychain.SupplyChainLinkBuilder;
import com.vmturbo.platform.sdk.common.supplychain.SupplyChainNodeBuilder;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntry;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntryType;
import com.vmturbo.platform.sdk.common.util.TargetDiscoveryResponse;
import com.vmturbo.platform.sdk.common.util.TargetValidationResponse;
import com.vmturbo.platform.sdk.probe.IProbe;
import com.vmturbo.platform.sdk.probe.builder.DatacenterBuilder;
import com.vmturbo.platform.sdk.probe.builder.DiskArrayBuilder;
import com.vmturbo.platform.sdk.probe.builder.PhysicalMachineBuilder;
import com.vmturbo.platform.sdk.probe.builder.StorageBuilder;
import com.vmturbo.platform.sdk.probe.builder.VirtualMachineBuilder;

/**
 * A simple example of probe implementation.
 */
public class SimpleProbe implements IProbe {

    private final Logger logger = Logger.getLogger(getClass());

    /**
     * Execute discovery of the target.
     *
     * @param accountDefinitionMap Account definition map.
     * @return A set of entity DTOs for retrieved service entities.
     */
    @Override
    public TargetDiscoveryResponse discoverTarget(Map<String, String> accountDefinitionMap) {
        logger.info("Discover Target");

        logger.info("Account Credentials:");
        for (String key : accountDefinitionMap.keySet()) {
            String value = accountDefinitionMap.get(key);
            logger.info(key + " : " + value);
        }

        final String DC1_NAME = "dc1";
        final String PM1_NAME = "pm1";
        final String DA1_NAME = "da1";
        final String ST1_NAME = "st1";
        final String VM1_NAME = "vm1";
        final String DC1_ID = "dc1-id";
        final String PM1_ID = "pm1-id";
        final String DA1_ID = "da1-id";
        final String ST1_ID = "st1-id";
        final String VM1_ID = "vm1-id";

        // Data center entity DTO
        DatacenterBuilder dcb = new DatacenterBuilder(DC1_ID);
        dcb.displayName(DC1_NAME)
        //commodities sold
        .space(100f,1f, null)
        .power(100f,1f, null)
        .cooling(100f,1f, null);
        EntityDTO dc = dcb.configure();
        // Physical machine entity DTO
        PhysicalMachineBuilder pmb = new PhysicalMachineBuilder(PM1_ID);
        pmb.displayName(PM1_NAME)
        //commodities sold
        .mem(100f, 1f, null)
        .cpu(100f, 1f, null)
        //commodities bought, with corresponding provider
        .datacenter(DC1_ID)
        .coolingBought(null, 1f)
        .powerBought(null, 1f)
        .spaceBought(null, 1f);

        EntityDTO pm = pmb.configure();

        // Disk Array entity DTO
        DiskArrayBuilder dab = new DiskArrayBuilder(DA1_ID);
        dab.displayName(DA1_NAME)
        //commodities sold
        .storageAccess(100f, 1f, null)
        .storageAmount(100f, 1f, null)
        .storageExtent(100f, 1f, null)
        .storageLatency(100f, 1f, null)
        .storageProvisioned(100f, 1f, null);

        EntityDTO da = dab.configure();

        // Storage entity DTO
        StorageBuilder stb = new StorageBuilder(ST1_ID);
        stb.displayName(ST1_NAME)
        //commodities sold
        .storageAccess(100f, 1f, null)
        .storageAmount(100f, 1f, null)
        .storageExtent(100f, 1f, null)
        .storageLatency(100f, 1f, null)
        .storageProvisioned(100f, 1f, null)
        //commodities bought with corresponding provider
        .diskArray(DA1_ID)
        .storageAccessBought(null, 1f)
        .storageAmountBought(null, 1f)
        .storageExtentBought(null, 1f)
        .storageLatencyBought(null, 1f)
        .storageProvisionedBought(null, 1f);
        EntityDTO st = stb.configure();


        // Virtual machine entity DTO
        VirtualMachineBuilder vmb = new VirtualMachineBuilder(VM1_ID);
        vmb.displayName(VM1_NAME)
        //commodities sold
        .vcpu(100f, 1f, null)
        .vmem(100f, 1f, null)
        .vstorage(100f, 1f, null)
        //commodities bought with corresponding provider
        .pm(PM1_ID)
        .cpuBought(null, 1f)
        .memBought(null, 1f)
        .storage(ST1_ID)
        .storageAccessBought(null, 1f)
        .storageAmountBought(null, 1f)
        .storageLatencyBought(null, 1f)
        .storageProvisionedBought(null, 1f);
        EntityDTO vm = vmb.configure();

        // Create the relationships between entities.
        // DC and PM
        dc.getConsistsOf().add(pm.getId());

        // PM and Storage
        pm.getUnderlying().add(st.getId());

        return new TargetDiscoveryResponse(Sets.newHashSet(dc, da, pm, st, vm));
    }

    /**
     * Get the supply chain for this probe. Buying / Selling relationship between service entities:
     * Data centers sell commodities to hosts. Hosts sell commodities to virtual machines. A disk
     * array sells commodities to storages. Storages sell commodities to physical and virtual
     * machines.
     *
     * @return A set of template DTOs for this probe.
     */
    @Override
    public Set<TemplateDTO> getSupplyChainDefinition() {
        logger.info("Get supply chain");
        SupplyChainBuilder scb = new SupplyChainBuilder();

        // VM
        SupplyChainNodeBuilder top = new SupplyChainNodeBuilder()
        .entity(Entity.VirtualMachine)
        .selling(Commodity.VCPU)
        .selling(Commodity.VMem)
        .selling(Commodity.VStorage);

        // PM
        SupplyChainNodeBuilder pmNode = new SupplyChainNodeBuilder()
        .entity(Entity.PhysicalMachine)
        .selling(Commodity.CPU)
        .selling(Commodity.Mem);

        // Storage
        SupplyChainNodeBuilder stNode = new SupplyChainNodeBuilder()
        .entity(Entity.Storage)
        .selling(Commodity.StorageAmount)
        .selling(Commodity.StorageAccess)
        .selling(Commodity.Extent)
        .selling(Commodity.StorageLatency)
        .selling(Commodity.StorageProvisioned);

        // Disk Array
        SupplyChainNodeBuilder daNode = new SupplyChainNodeBuilder()
        .entity(Entity.DiskArray)
        .selling(Commodity.StorageAmount)
        .selling(Commodity.StorageLatency)
        .selling(Commodity.StorageProvisioned)
        .selling(Commodity.Extent)
         .autoCreate(true);


        // Datacenter
        SupplyChainNodeBuilder dcNode = new SupplyChainNodeBuilder()
        .entity(Entity.DataCenter)
        .selling(Commodity.Space)
        .selling(Commodity.Power)
        .selling(Commodity.Cooling);

        // Link from VM to PM
        SupplyChainLinkBuilder vm2pm = new SupplyChainLinkBuilder();
        vm2pm.link(Entity.VirtualMachine, Entity.PhysicalMachine, ProviderType.HOSTING)
            .commodity(Commodity.CPU)
            .commodity(Commodity.Mem);
        EntityLink top2pmLink = vm2pm.build();
        // Link from VM to ST
        SupplyChainLinkBuilder vm2st = new SupplyChainLinkBuilder();
        vm2st.link(Entity.VirtualMachine, Entity.Storage,ProviderType.LAYEREDOVER)
            .commodity(Commodity.StorageAmount)
            .commodity(Commodity.StorageAccess)
            .commodity(Commodity.StorageProvisioned)
            .commodity(Commodity.StorageLatency);
        EntityLink top2stLink = vm2st.build();
        // Link from PM to DC
        SupplyChainLinkBuilder pm2dc = new SupplyChainLinkBuilder();
        pm2dc.link(Entity.PhysicalMachine, Entity.DataCenter,ProviderType.HOSTING)
            .commodity(Commodity.Cooling)
            .commodity(Commodity.Power)
            .commodity(Commodity.Space);
        EntityLink pm2dcLink = pm2dc.build();

        // Link from ST to DA
        SupplyChainLinkBuilder st2da = new SupplyChainLinkBuilder();
        st2da.link(Entity.Storage, Entity.DiskArray,ProviderType.HOSTING)
            .commodity(Commodity.StorageAccess)
            .commodity(Commodity.StorageAmount)
            .commodity(Commodity.Extent)
            .commodity(Commodity.StorageLatency)
            .commodity(Commodity.StorageProvisioned);
        EntityLink stToDaLink = st2da.build();


        // Top Node - Connect VM to PM
        scb.top(top)
        .connectsTo(pmNode, top2pmLink)
        .connectsTo(stNode, top2stLink)
        // Next Node - Connect PM to DC
        .entity(pmNode)
        .connectsTo(dcNode, pm2dcLink)
        // Next node - Connects ST to DA
        .entity(stNode)
        .connectsTo(daNode, stToDaLink)
        .entity(daNode)
        // Last Node - no more connections
        .entity(dcNode);

        return scb.configure();
    }

    /**
     * Returns the fields and their meta data required by the probe to validate and discover the
     * targets as a set of {@link AccountDefinitionEntry} objects.<br>
     * Each Account Definition Entry denotes the meta data for the field required by the probe to
     * reach the target, the value for which will be provided by the user. Each fields is defined as
     * optional or mandatory.
     *
     * @return The account definition map.
     */
    @Override
    public Map<String, AccountDefinitionEntry> getAccountDefinitionEntryMap() {
        logger.info("Get account definition");

        //AccountDefinitionEntry entry = SDKUtil.setTargetIdentifierEntry(displayname, description, mandatory, verification_regex);
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
                                                       AccountDefinitionEntryType.Mandatory, ".*"),
                            /*
                             * This is an optional field in the account definition
                             */
                            AccountDefinitionEntry.VERSION_FIELD,
                            new AccountDefinitionEntry(AccountDefinitionEntry.VERSION_FIELD,
                                                       "Version", "target version",
                                                       AccountDefinitionEntryType.Optional, ".*"));
        return accountDefinitionEntryMap;
    }

    /**
     * Validate the target.
     *
     * @param accountDefinitionMap Account definition map.
     * @return The message of target validation status.
     */
    @Override
    public TargetValidationResponse validateTarget(Map<String, String> accountValues) {
        logger.info("Validate Target");
        return TargetValidationResponse.createOkResponse();
    }

}
