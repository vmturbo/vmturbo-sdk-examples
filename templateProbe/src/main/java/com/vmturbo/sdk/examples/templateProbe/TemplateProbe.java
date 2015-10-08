package com.vmturbo.sdk.examples.templateProbe;

import java.util.Map;
import java.util.Set;

import com.vmturbo.platform.sdk.common.DTO.TemplateDTO;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntry;
import com.vmturbo.platform.sdk.common.util.TargetDiscoveryResponse;
import com.vmturbo.platform.sdk.common.util.TargetValidationResponse;
import com.vmturbo.platform.sdk.probe.IProbe;

public class TemplateProbe implements IProbe {

	@Override
	public Set<TemplateDTO> getSupplyChainDefinition() {
		// TODO Write code here to implement the Supply Chain Definition
		return null;
	}

	@Override
	public Map<String, AccountDefinitionEntry> getAccountDefinitionEntryMap() {
		// TODO Write code here to implement the Account Definition Map
		return null;
	}

	@Override
	public TargetDiscoveryResponse discoverTarget(Map<String, String> accountDefinitionMap) {
		// TODO Write code here to implement the target discovery
		return null;
	}

	@Override
	public TargetValidationResponse validateTarget(Map<String, String> accountDefinitionMap) {
		// TODO Write code here to implement the target validation
		return null;
	}



}
