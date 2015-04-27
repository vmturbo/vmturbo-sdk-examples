package com.vmturbo.sdk.examples.templateProbe;

import java.util.Map;
import java.util.Set;


import com.vmturbo.platform.sdk.common.DTO.EntityDTO;
import com.vmturbo.platform.sdk.common.DTO.TemplateDTO;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntry;
import com.vmturbo.platform.sdk.common.util.TargetValidationResponse;
import com.vmturbo.platform.sdk.probe.AbstractProbe;

public class TemplateProbe extends AbstractProbe {

	@Override
	protected Set<TemplateDTO> getSupplyChainDefinition() {
		// TODO Write code here to implement the Supply Chain Definition
		return null;
	}

	@Override
	protected Map<String, AccountDefinitionEntry> getAccountDefinitionEntryMap() {
		// TODO Write code here to implement the Account Definition Map
		return null;
	}

	@Override
	protected Set<EntityDTO> discoverTarget(Map<String, String> accountDefinitionMap) {
		// TODO Write code here to implement the target discovery
		return null;
	}

	@Override
	protected TargetValidationResponse validateTarget(Map<String, String> accountDefinitionMap) {
		// TODO Write code here to implement the target validation
		return null;
	}



}
