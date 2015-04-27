package com.vmturbo.sdk.examples.simpleProbe;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.vmturbo.platform.sdk.common.DTO.CommodityDTO;
import com.vmturbo.platform.sdk.common.DTO.EntityDTO;
import com.vmturbo.platform.sdk.common.DTO.ModelEnum.Commodity;
import com.vmturbo.platform.sdk.common.DTO.ModelEnum.Entity;
import com.vmturbo.platform.sdk.common.DTO.ModelEnum.TemplateType;
import com.vmturbo.platform.sdk.common.DTO.TemplateDTO;
import com.vmturbo.platform.sdk.common.DTO.TemplateDTO.Provider;
import com.vmturbo.platform.sdk.common.DTO.TemplateDTO.ProviderType;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntry;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntryType;
import com.vmturbo.platform.sdk.common.util.ResponseCode;
import com.vmturbo.platform.sdk.common.util.TargetValidationResponse;
import com.vmturbo.platform.sdk.probe.AbstractProbe;

/**
 * A simple example of probe implementation.
 */
public class SimpleProbe extends AbstractProbe {

	private static final Logger logger = Logger.getLogger("com.vmturbo.sdk.examples.simpleProbe");
	private static final String LOGPREFIX = "-- SimpleProbeExample -- : ";

	/**
	 * Execute discovery of the target.
	 *
	 * @param accountDefinitionMap    Account definition map.
	 * @return A set of entity DTOs for retrieved service entities.
	 */
	@Override
	protected Set<EntityDTO> discoverTarget(Map<String, String> accountDefinitionMap) {
		logger.info(LOGPREFIX + "Discover Target");

		logger.info("Account Credentials:");
		for (String key: accountDefinitionMap.keySet()) {
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
		CommodityDTO commSoldDc = new CommodityDTO(Commodity.Space, null, 1f, 100f);
		EntityDTO dc = new EntityDTO(Entity.DataCenter, DC1_ID, DC1_NAME, Lists.newArrayList(commSoldDc), null);

		// Physical machine entity DTO
		CommodityDTO commSoldPm = new CommodityDTO(Commodity.CPU, null, 1f, 100f);
		CommodityDTO commBoughtPmDc = new CommodityDTO(Commodity.Space, null, 1f, 100f);
		Map<String, List<CommodityDTO>> commBoughtPmMap = EntityDTO.createCommBoughtMap(DC1_ID, commBoughtPmDc);
		EntityDTO pm = new EntityDTO(Entity.PhysicalMachine, PM1_ID, PM1_NAME, Lists.newArrayList(commSoldPm), commBoughtPmMap);

		// Disk array entity DTO
		CommodityDTO commSoldDa = new CommodityDTO(Commodity.StorageAmount, null, 1f, 100f);
		EntityDTO da = new EntityDTO(Entity.DiskArray, DA1_ID, DA1_NAME, Lists.newArrayList(commSoldDa), null);

		// Storage entity DTO
		CommodityDTO commSoldSt = new CommodityDTO(Commodity.StorageAmount, null, 1f, 100f);
		CommodityDTO commBoughtStDa = new CommodityDTO(Commodity.StorageAmount, null, 1f, 100f);
		Map<String, List<CommodityDTO>> commBoughtStMap = EntityDTO.createCommBoughtMap(DA1_ID, commBoughtStDa);
		EntityDTO st = new EntityDTO(Entity.Storage, ST1_ID, ST1_NAME, Lists.newArrayList(commSoldSt), commBoughtStMap);

		// Virtual machine entity DTO
		CommodityDTO commSoldVm = new CommodityDTO(Commodity.VCPU, null, 1f, 100f);
		CommodityDTO commBoughtVmSt = new CommodityDTO(Commodity.StorageAmount, null, 1f, 100f);
		CommodityDTO commBoughtVmPm = new CommodityDTO(Commodity.CPU, null, 1f, 100f);
		Map<String, List<CommodityDTO>> commBoughtMap = EntityDTO.createCommBoughtMap(PM1_ID, commBoughtVmPm);
		EntityDTO.putCommBoughtInMap(ST1_ID, commBoughtVmSt, commBoughtMap);
		EntityDTO vm = new EntityDTO(Entity.VirtualMachine, VM1_ID, VM1_NAME, Lists.newArrayList(commSoldVm), commBoughtMap);

		// Create the relationships between entities.
		// DC and PM
		dc.getConsistsOf().add(pm.getId());

		// PM and Storage
		pm.getUnderlying().add(st.getId());

		return Sets.newHashSet(dc, da, pm, st, vm);
	}

	/**
	 * Get the supply chain for this probe.
	 * Buying / Selling relationship between service entities:
	 *     Data centers sell commodities to hosts.
	 *     Hosts sell commodities to virtual machines.
	 *     A disk array sells commodities to storages.
	 *     Storages sell commodities to physical and virtual machines.
	 *
	 * @return A set of template DTOs for this probe.
	 */
	@Override
	protected Set<TemplateDTO> getSupplyChainDefinition() {
		logger.info(LOGPREFIX + "Get supply chain");

		// Data center
		CommodityDTO commSoldDc = new CommodityDTO(Commodity.Space, null, 1f, 100f);
		TemplateDTO dc = new TemplateDTO(Entity.DataCenter, Lists.newArrayList(commSoldDc), null,
				TemplateType.Base, 0);

		// Physical machine
		CommodityDTO commSoldPm = new CommodityDTO(Commodity.CPU, null, 1f, 100f);
		CommodityDTO commBoughtPmDc = new CommodityDTO(Commodity.Space, null, 1f, 100f);
		Provider provider = new Provider(Entity.DataCenter, ProviderType.HOSTING, 1, 1);
		Map<Provider, List<CommodityDTO>> commBoughtPmMap = TemplateDTO.createCommBoughtMap(provider, commBoughtPmDc);
		TemplateDTO pm = new TemplateDTO(Entity.PhysicalMachine, Lists.newArrayList(commSoldPm), commBoughtPmMap,
				TemplateType.Base, 0);

		// Disk array
		CommodityDTO commSoldDa = new CommodityDTO(Commodity.StorageAmount, null, 1f, 100f);
		TemplateDTO da = new TemplateDTO(Entity.DiskArray, Lists.newArrayList(commSoldDa), null,
				TemplateType.Base, 0);

		// Storage
		CommodityDTO commSoldSt = new CommodityDTO(Commodity.StorageAmount, null, 1f, 100f);
		CommodityDTO commBoughtStDa = new CommodityDTO(Commodity.StorageAmount, null, 1f, 100f);
		provider = new Provider(Entity.DiskArray, ProviderType.HOSTING, 1, 1);
		Map<Provider, List<CommodityDTO>> commBoughtStMap = TemplateDTO.createCommBoughtMap(provider, commBoughtStDa);
		TemplateDTO st = new TemplateDTO(Entity.Storage, Lists.newArrayList(commSoldSt), commBoughtStMap,
				TemplateType.Base, 0);

		// Virtual machine
		CommodityDTO commSoldVm = new CommodityDTO(Commodity.VCPU, null, 1f, 100f);
		CommodityDTO commBoughtVmSt = new CommodityDTO(Commodity.StorageAmount, null, 1f, 100f);
		CommodityDTO commBoughtVmPm = new CommodityDTO(Commodity.CPU, null, 1f, 100f);
		provider = new Provider(Entity.PhysicalMachine, ProviderType.HOSTING, 1, 1);
		Provider provider2 = new Provider(Entity.Storage, ProviderType.LAYEREDOVER, 1, 1);
		Map<Provider, List<CommodityDTO>> commBoughtMap = TemplateDTO.createCommBoughtMap(provider, commBoughtVmPm);
		TemplateDTO.putCommBoughtInMap(provider2, commBoughtVmSt, commBoughtMap);
		TemplateDTO vm = new TemplateDTO(Entity.VirtualMachine, Lists.newArrayList(commSoldVm), commBoughtMap,
				TemplateType.Base, 0);

		return Sets.newHashSet(dc, da, pm, st, vm);
	}

	/**
	 * Returns the fields and their meta data required by the probe to validate and discover the targets 
	 * as a set of {@link AccountDefinitionEntry} objects.<br>
	 * Each Account Definition Entry denotes the meta data for the field required by the probe
	 * to reach the target, the value for which will be provided by the user.
	 * Each fields is defined as optional or mandatory.
	 *
	 * @return The account definition map.
	 */
	@Override
	protected Map<String, AccountDefinitionEntry> getAccountDefinitionEntryMap() {
		logger.info(LOGPREFIX + "Get account definition");

		//AccountDefinitionEntry entry = SDKUtil.setTargetIdentifierEntry(displayname, description, mandatory, verification_regex);
		ImmutableMap<String, AccountDefinitionEntry> accountDefinitionEntryMap = ImmutableMap.of(
				/*
				 * This mandatory field denotes the instance name of the target. 
				 */
				AccountDefinitionEntry.TARGET_IDENTIFIER, new AccountDefinitionEntry(AccountDefinitionEntry.TARGET_IDENTIFIER, "Name", "name of the target", AccountDefinitionEntryType.Mandatory, ".*"),
				/*
				 * This mandatory field denotes the user name required to connect to the target
				 */
				AccountDefinitionEntry.USERNAME_FIELD, new AccountDefinitionEntry(AccountDefinitionEntry.USERNAME_FIELD, "User", "username to login to the target", AccountDefinitionEntryType.Mandatory, ".*"),
				/*
				 * This mandatory field denotes the password required to connect to the target
				 */
				AccountDefinitionEntry.PASSWORD_FIELD, new AccountDefinitionEntry(AccountDefinitionEntry.PASSWORD_FIELD, "Password", "password for the account", AccountDefinitionEntryType.Mandatory, ".*"),
				/*
				 * This is an optional field in the account definition
				 */
				AccountDefinitionEntry.VERSION_FIELD, new AccountDefinitionEntry(AccountDefinitionEntry.VERSION_FIELD, "Version", "target version", AccountDefinitionEntryType.Optional, ".*")
				);
		return accountDefinitionEntryMap;
	}

	/**
	 * Validate the target.
	 *
	 * @param accountDefinitionMap    Account definition map.
	 * @return The message of target validation status.
	 */
	@Override
	protected TargetValidationResponse validateTarget(Map<String, String> accountValues) {
		logger.info(LOGPREFIX +"Validate Target");
		TargetValidationResponse validationResponse = new TargetValidationResponse();
		validationResponse.targetValidationStatus = ResponseCode.SUCCESS;
		validationResponse.targetValidationExplanation = "Test Probe Validated";
		return validationResponse;
	}

}