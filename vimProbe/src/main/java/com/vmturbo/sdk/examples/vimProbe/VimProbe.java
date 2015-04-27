package com.vmturbo.sdk.examples.vimProbe;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.PerfCounterInfo;
import com.vmware.vim25.PerfEntityMetric;
import com.vmware.vim25.PerfEntityMetricBase;
import com.vmware.vim25.PerfMetricId;
import com.vmware.vim25.PerfMetricIntSeries;
import com.vmware.vim25.PerfMetricSeries;
import com.vmware.vim25.PerfQuerySpec;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedObject;
import com.vmware.vim25.mo.PerformanceManager;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.util.PropertyCollectorUtil;
import com.vmware.vim25.DatastoreInfo;


/**
 * An example implementation of the VCenter probe.
 */
public class VimProbe extends AbstractProbe {

	private static final Logger logger = Logger.getLogger("com.vmturbo.platform.container.mediation");
	private static final String LOGPREFIX = "-- VcProbeExample -- : ";
	private static final String METRIC_CPU_USED = "cpu.usagemhz.average";
	private static final String METRIC_MEM_USED = "mem.consumed.average";
	private static final String PROPERTY_VM_UUID = "config.uuid";
	private static final String PROPERTY_VM_NAME = "name";
	private static final String PROPERTY_VM_HOST = "runtime.host";
	private static final String PROPERTY_VM_NUM_CPU = "config.hardware.numCPU";
	private static final String PROPERTY_VM_MEM_SIZE = "config.hardware.memoryMB";
	private static final String PROPERTY_HOST_UUID = "summary.hardware.uuid";
	private static final String PROPERTY_HOST_NAME = "name";
	private static final String PROPERTY_HOST_MEM_SIZE = "summary.hardware.memorySize";
	private static final String PROPERTY_HOST_CPU_MHZ = "summary.hardware.cpuMhz";
	private static final String PROPERTY_HOST_NUM_CPU_THREADS = "summary.hardware.numCpuThreads";

	private static final String SE_DC = "Datacenter";
	private static final String SE_DC_DISP_NAME = "Datacenter-VC";
	private static final String SE_DC_ID = "Datacenter-VC-ID";
	private static final String SE_HOST = "HostSystem";
	private static final String SE_VM = "VirtualMachine";

	private static final String USERNAME = "username";
	private static final String PASSWORD = "password";
	private static final String TARGET_IDENTIFIER = "targetIdentifier";

	ImmutableMap<String, AccountDefinitionEntry> accountDefinitionEntryMap = ImmutableMap.of(
			/*
			 * 
			 */
			AccountDefinitionEntry.TARGET_IDENTIFIER, new AccountDefinitionEntry(AccountDefinitionEntry.TARGET_IDENTIFIER, "name/IP address", "name or IP address to access the target", AccountDefinitionEntryType.Mandatory, ".*"),
			/*
			 * Mandatory user name field required to connect to the VSphere target
			 */
			AccountDefinitionEntry.USERNAME_FIELD, new AccountDefinitionEntry(AccountDefinitionEntry.USERNAME_FIELD, "Username", "username to login to the target", AccountDefinitionEntryType.Mandatory, ".*"),
			/*
			 * Mandatory password field required to connect to the VSphere target
			 */
			AccountDefinitionEntry.PASSWORD_FIELD, new AccountDefinitionEntry(AccountDefinitionEntry.PASSWORD_FIELD, "Password", "password for the account", AccountDefinitionEntryType.Mandatory, ".*")
			);

	private ServiceInstance si;
	private PerformanceManager perfMgr;
	
	/**
	 *  Map of counter IDs indexed by counter name.
     */
	private Map<String, Integer> countersIdMap = new HashMap<String, Integer>();

	/**
	 *  Map of performance counter data (PerfCounterInfo) indexed by counter ID.
     */
	private Map<Integer, PerfCounterInfo> countersInfoMap = new HashMap<Integer, PerfCounterInfo>();

	/**
	 *  Map of entity data map indexed by the entity uuid.
	 *  This map is used in parsing VM to get the CPU information of its Host.
     */
	private Map<String, Map<String,Object>> entityInfoMap = new HashMap<String, Map<String,Object>>();

	/**
	 * Discover Target
	 * @param accountValues		Map representing the values for the fields in the AccountDefintion 
	 * 							required for discovering the target
	 * @return					Entities discovered by the probe as a set of EntityDTO
	 * 
	 */
	@Override
	protected Set<EntityDTO> discoverTarget(Map<String, String> accountValues) {
		logger.info(LOGPREFIX + "Discover Target");
		logger.info(LOGPREFIX + "Connecting to target: " + accountValues.get(AccountDefinitionEntry.NAME_OR_ADDRESS_FIELD));

		Set<EntityDTO> entityDTOSet = new HashSet<EntityDTO>();

		// Connect to target
		si = connectVC(accountValues);
		if (si == null) {
			logger.warn(LOGPREFIX + "Failed connecting to target: " + accountValues.get(AccountDefinitionEntry.NAME_OR_ADDRESS_FIELD));
			return entityDTOSet;
		}
		perfMgr = si.getPerformanceManager();

		// Create property map
		String[] moClsNames = {SE_DC, SE_HOST, SE_VM};
		Map<String, String[]> propsMap = new HashMap<String, String[]>();
		String[] dcProps = {"",""};
		String[] vmProps = {PROPERTY_VM_UUID, PROPERTY_VM_NAME, PROPERTY_VM_HOST, PROPERTY_VM_NUM_CPU, PROPERTY_VM_MEM_SIZE};
		String[] pmProps = {PROPERTY_HOST_UUID, PROPERTY_HOST_NAME, PROPERTY_HOST_MEM_SIZE, PROPERTY_HOST_CPU_MHZ, PROPERTY_HOST_NUM_CPU_THREADS};
		propsMap.put(moClsNames[0], dcProps);
		propsMap.put(moClsNames[1], pmProps);
		propsMap.put(moClsNames[2], vmProps);

		// Load countersIdMap and countersInfoMap
		resetPerfCounterMaps();
		loadPerfCounterMaps(perfMgr);

		// Iterate over the managed entities to generate entity DTOs.
		for (String moCls : moClsNames) {
			logger.info(LOGPREFIX + "Processing managed object class: " + moCls);
			entityDTOSet.addAll(processManagedObject(moCls, propsMap.get(moCls)));
		}

		// Logout from target
		si.getServerConnection().logout();

		return entityDTOSet;
	}

	/**
	 * Connect to a VC target. 
	 * @param accountValues		Map representing the values for the fields in the AccountDefintion 
	 * 							required for discovering the target
	 * @return                  A service instance of the target.
	 */
	protected ServiceInstance connectVC(Map<String, String> accountValues) {
		final String targetAddr = accountValues.get(AccountDefinitionEntry.NAME_OR_ADDRESS_FIELD);
		final String username = accountValues.get(USERNAME);
		final String password = accountValues.get(PASSWORD); 
		final String url = "https://" + targetAddr + "/sdk";
		int timeout = 60*1000; // 60sec timeout for connection 
		String namespace = ServiceInstance.VIM25_NAMESPACE;

		ServiceInstance servInst = null;

		try {
			servInst = new ServiceInstance(new URL(url), username, password, true, namespace);
			servInst.getServerConnection().getVimService().getWsc().setConnectTimeout(timeout);
		} catch (Exception e) {
			return null;
		}

		return servInst;
	}

	/**
	 * Retrieve properties from VC and generate entity DTOs. 
	 * @param moCls      Managed object class name
	 * @param propPaths	 property paths for the managed object class
	 * @return           An EntityDTO set representing the service entities for the managed object class.
	 */
	@SuppressWarnings("unchecked")
	protected Set<EntityDTO> processManagedObject(String moCls, String[] propPaths) {
		Set<EntityDTO> edSet = new HashSet<EntityDTO>();

		// Create a data center
		if (moCls.equals(SE_DC)) {
			EntityDTO ed = null;
			try {
				ed = parseDatacenter(null, null);
			} catch (Exception e) {
				logger.error(LOGPREFIX + "Failed parsing data center.");
			}
			edSet.add(ed);
			return edSet;
		}

		// Get the managed object instances from the root node for class as specified 
		// in the first part of the class string (top level).
		ManagedObject[] mos = null;
		try {
			mos = new InventoryNavigator(si.getRootFolder()).searchManagedEntities(moCls);
		} catch (Exception e) {
			logger.error(LOGPREFIX + "Failed searching managed entitties.");
		}
		if (mos.length == 0) return edSet;

		// Retrieve properties from VC for the given list of managed entity instances
		Hashtable<String, Object>[] pTables = null;
		try {
			pTables = PropertyCollectorUtil.retrieveProperties(mos, moCls, propPaths);
		} catch (Exception e) {
			logger.error(LOGPREFIX + "Failed retrieving properties.");
		}

		// Generate Entity DTOs from the managed object references
		int pTableLen = pTables.length;
		for (int i = 0; i < pTableLen; i++) {
			ManagedObjectReference mor = mos[i].getMOR();
			EntityDTO ed = generateEntityDTO(mor, pTables[i]);
			edSet.add(ed);
		}

		return edSet;
	}

	/**
	 * Parse ManagedObjectReference retrieved from VC. 
	 * Now, only consider service entities: Datacenter, HostSystem, and VirtualMachine.
	 * @param mor    ManagedObjectReference
	 * @param props	 Map of property and value for the mos object
	 * @return       An EntityDTO representing the service entity.
	 */
	private EntityDTO generateEntityDTO(ManagedObjectReference mor, Hashtable<String,Object> props) {
		EntityDTO ed = null;
		String morType = mor.getType();
		if (SE_VM.equals(morType)) {
			ed = parseVM(mor, props);
		} else if (SE_HOST.equals(morType)) {
			ed = parseHost(mor, props);
		} else if (SE_DC.equals(morType)) {
			ed = parseDatacenter(mor, props);
		}

		return ed;
	}

	/**
	 * Parse VM ManagedObjectReference retrieved from VC
	 * @param mor    Managed object reference for the VM
	 * @param props	 Map of property and value for the mos object
	 * @return       An EntityDTO representing the service entity of VM.
	 */
	public EntityDTO parseVM(ManagedObjectReference mor, Hashtable<String,Object> props) {
		String vmName = props.get(PROPERTY_VM_NAME).toString();
		Integer numCPU = (Integer) props.get(PROPERTY_VM_NUM_CPU);
		Integer memory = (Integer) props.get(PROPERTY_VM_MEM_SIZE);
		ManagedObjectReference hostMor = (ManagedObjectReference) props.get(PROPERTY_VM_HOST);
		String hostName = hostMor.getVal();

		EntityDTO vm = new EntityDTO(null, null, null, null, null);
		List<CommodityDTO> commList = new ArrayList<CommodityDTO>();
		Map<String, List<CommodityDTO>> commBoughtMap = new HashMap<String, List<CommodityDTO>>();
		vm.setCommodBoughtMap(commBoughtMap);
		vm.setCommodSoldList(commList);
		vm.setEntity(Entity.VirtualMachine);
		vm.setId(vmName);
		vm.setDisplayName(vmName);

		// Query performance for "used" data of CPU and Memory
		float cpuUsed = 0;
        float memUsed = 0;
        String[] counterNames = new String[]{METRIC_CPU_USED, METRIC_MEM_USED};
        try {
    		Map<String, Object> perfMap = queryPerf(mor, counterNames);
    		if (perfMap != null && perfMap.size() > 0) {
    			cpuUsed = Float.parseFloat(perfMap.get(METRIC_CPU_USED).toString());
            	memUsed = Float.parseFloat(perfMap.get(METRIC_MEM_USED).toString())/1024; // in MB
    		}
		} catch (Exception e) {
			logger.error("Error while parsing usage in VM: ", e);
		}

		// VCPU commodity
		float coreMhz = (Integer) entityInfoMap.get(hostName).get(PROPERTY_HOST_CPU_MHZ);
		float cpuCapacity = (float)coreMhz*numCPU;
		CommodityDTO vcpuCommodity = new CommodityDTO(Commodity.VCPU,null,cpuUsed,cpuCapacity);
		commList.add(vcpuCommodity);

		// VMem commodity
		float memCapacity = (float)memory;
		CommodityDTO vmemCommodity = new CommodityDTO(Commodity.VMem,null,memUsed,memCapacity);
		commList.add(vmemCommodity);

		// Commodity Bought
		CommodityDTO cpuBought = new CommodityDTO(Commodity.CPU, null, cpuUsed, cpuCapacity);
		CommodityDTO memBought = new CommodityDTO(Commodity.Mem, null, memUsed, memCapacity);

		EntityDTO.putCommBoughtInMap(hostName, Arrays.asList(cpuBought,memBought), commBoughtMap);

		return vm;
	}

	/**
	 * Parse the properties table for the Host managed object reference instance.
	 * @param mor    Managed object reference for Host
	 * @param props	 Map of property and value for the mos object
	 * @return       An EntityDTO representing the service entity of host.
	 */
	public EntityDTO parseHost(ManagedObjectReference mor, Hashtable<String,Object> props) {
		String name = mor.get_value();
		Integer cpuMhz = (Integer) props.get(PROPERTY_HOST_CPU_MHZ);
		Short numCpuThreads = (Short) props.get(PROPERTY_HOST_NUM_CPU_THREADS);
		Long mem = (Long) props.get(PROPERTY_HOST_MEM_SIZE);

		EntityDTO pm = new EntityDTO(null, null, null, null, null);
		List<CommodityDTO> commList = new ArrayList<CommodityDTO>();
		Map<String, List<CommodityDTO>> commBoughtMap = new HashMap<String, List<CommodityDTO>>();
		pm.setCommodBoughtMap(commBoughtMap);
		pm.setCommodSoldList(commList);
		pm.setEntity(Entity.PhysicalMachine);
		pm.setDisplayName(name);
		pm.setId(name);

		// Add the "summary.hardware.cpuMhz" information to the map
		Map<String,Object> pmChildMap = new HashMap<String,Object>();
		entityInfoMap.put(name, pmChildMap);
		pmChildMap.put(PROPERTY_HOST_CPU_MHZ, cpuMhz);

		// Query performance for "used" data of CPU and Memory
		float cpuUsed = 0;
        float memUsed = 0;
        String[] counterNames = new String[]{METRIC_CPU_USED, METRIC_MEM_USED};
        try {
    		Map<String, Object> perfMap = queryPerf(mor, counterNames);
    		if (perfMap != null && perfMap.size() > 0) {
    			cpuUsed = Float.parseFloat(perfMap.get(METRIC_CPU_USED).toString());
            	memUsed = Float.parseFloat(perfMap.get(METRIC_MEM_USED).toString())/1024; // in MB
    		}
		} catch (NumberFormatException e) {
			logger.error("Error while parsing usage in PM: " + e.toString());
		}

		// CPU commodity
        float cpuCapacity = (float)(cpuMhz*numCpuThreads);
		CommodityDTO cpuCommodity = new CommodityDTO(Commodity.CPU, null, cpuUsed, cpuCapacity);
		commList.add(cpuCommodity);

		// Mem commodity
        float memCapacity = (float)mem/1024/1024;
		CommodityDTO memCommodity = new CommodityDTO(Commodity.Mem, null, memUsed, memCapacity);
		commList.add(memCommodity);

		// Commodities bought from data center
		CommodityDTO spaceBought = new CommodityDTO(Commodity.Space, null, 1f, 100f);
		CommodityDTO powerBought = new CommodityDTO(Commodity.Power, null, 1f, 100f);
		CommodityDTO coolingBought = new CommodityDTO(Commodity.Cooling, null, 1f, 100f);

		EntityDTO.putCommBoughtInMap(SE_DC_ID, Arrays.asList(spaceBought,powerBought,coolingBought), commBoughtMap);

		return pm;
	}//end parseHost

	/**
	 * Parse the properties table for the DataCenter managed object reference instance.
	 * @param mor    Managed object reference for Datacenter
	 * @param props	 Map of property and value for the mos object
	 * @return       An EntityDTO representing the service entity of data center.
	 */
	public EntityDTO parseDatacenter(ManagedObjectReference mor, Hashtable<String,Object> props) {
		EntityDTO dc = new EntityDTO(null, null, null, null, null);
		List<CommodityDTO> commList = new ArrayList<CommodityDTO>();
		Map<String, List<CommodityDTO>> commBoughtMap = new HashMap<String, List<CommodityDTO>>();
		dc.setCommodBoughtMap(commBoughtMap);
		dc.setCommodSoldList(commList);
		dc.setEntity(Entity.DataCenter);
		dc.setDisplayName(SE_DC_DISP_NAME);
		dc.setId(SE_DC_ID);

		CommodityDTO space = new CommodityDTO(Commodity.Space, null, 1f, 100f);
		CommodityDTO power = new CommodityDTO(Commodity.Power, null, 1f, 100f);
		CommodityDTO cooling = new CommodityDTO(Commodity.Cooling, null, 1f, 100f);
		commList.add(space);
		commList.add(power);
		commList.add(cooling);

		return dc;
	}//end parseDatacenter

	/**
	 * Reset countersInfoMap and countersIdMap.
	 */
	protected void resetPerfCounterMaps() {
		countersInfoMap.clear();
		countersIdMap.clear();
	}

	/**
	 * Load countersInfoMap and countersIdMap.
	 * @param perfMgr    the Performance Manager for the VC target.
	 */
	protected void loadPerfCounterMaps(PerformanceManager perfMgr) {
		PerfCounterInfo[] perfCounters = perfMgr.getPerfCounter();
		// Cycle through the PerfCounterInfo objects and load the maps.
		for(PerfCounterInfo perfCounter : perfCounters) {
			Integer counterId = new Integer(perfCounter.getKey());
			countersInfoMap.put(counterId, perfCounter);

			String fullCounterName = getCounterName(perfCounter);
			countersIdMap.put(fullCounterName, counterId);
		}
	}

	/**
	 * Get the counter full name from a PerfCounterInfo object.
	 * @param perfCounter Performance counter info.
	 * @param props	      Map of property and value for the mos object
	 * @return            The counter full name.
	 */
	protected String getCounterName(PerfCounterInfo perfCounter) {
		String counterGroup = perfCounter.getGroupInfo().getKey();
		String counterName = perfCounter.getNameInfo().getKey();
		String counterRollupType = perfCounter.getRollupType().toString();
		String fullCounterName = counterGroup + "." + counterName + "." + counterRollupType;
		return fullCounterName;
	}

	/**
	 * Get the counter full name from a counter ID.
	 * countersInfoMap is used to get the corresponding PerfCounterInfo object.
	 * @param counterId		Counter ID.
	 * @return              The counter full name.
	 */
	protected String getCounterName(int counterId) {
		PerfCounterInfo perfCounter = countersInfoMap.get(counterId);
		return getCounterName(perfCounter);
	}

	/**
	 * Create a performance query specification.
	 * @param mor		    Managed object reference.
	 * @param counterNames	The name list of performance metrics.
	 * @return              The performance query specification.
	 */
	protected PerfQuerySpec createPerfQuerySpec(ManagedObjectReference mor, String[] counterNames) { 
		// Create PerfMetricIds for each counter.
		PerfMetricId[] perfMetricIds = new PerfMetricId[counterNames.length];

		int counterNamesLen = counterNames.length;
		for(int i = 0; i < counterNamesLen; i++) {
			// Create the PerfMetricId object for the counterName.
			PerfMetricId metricId = new PerfMetricId();

			// Get the ID for this counter.
			int counterId = countersIdMap.get(counterNames[i]);
			metricId.setCounterId(counterId);
			metricId.setInstance("*");
			perfMetricIds[i] = metricId;
		}

		// Create the query specification.
		PerfQuerySpec pqs = new PerfQuerySpec();
		pqs.setEntity(mor);
		pqs.setIntervalId(300); // Set sampling period as 300 seconds
		pqs.setFormat("normal");
		pqs.setMetricId(perfMetricIds);

		return pqs;
	}

	/**
	 * Retrieve the performance metrics.
	 * @param mor			Managed object reference.
	 * @param counterNames	The name list of performance metrics.
	 * @return              The performance map of values of performance metrics, indexed by counter names.
	 */
	public Map<String, Object> queryPerf(ManagedObjectReference mor, String[] counterNames) {
		// The performance map.
		Map<String, Object> perfMap = new HashMap<String, Object> ();

		// Create the performance query specification for this MOR.
		PerfQuerySpec querySpec = createPerfQuerySpec(mor, counterNames);
		PerfQuerySpec[] pqs = new PerfQuerySpec[]{querySpec};

		List<PerfEntityMetricBase[]> pems_list = new ArrayList<PerfEntityMetricBase[]> ();

		try {
			// Performance query with the performance manager.
			PerfEntityMetricBase[] pems = perfMgr.queryPerf(pqs);
			if (pems != null) pems_list.add(pems);
		} catch (Exception e) {
			logger.error(LOGPREFIX + "Performance query error: ", e);
		}

		// Retrieve the values of performance metrics from the query results. 
		for (PerfEntityMetricBase[] pems : pems_list) {
			for (PerfEntityMetricBase pemb : pems) {
				PerfEntityMetric pem1 = (PerfEntityMetric)pemb;
				PerfMetricSeries[] pmsList = pem1.getValue();
				if (pmsList == null) continue; // No data available
				for (PerfMetricSeries pms : pmsList) {
					PerfMetricIntSeries pmis = (PerfMetricIntSeries)pms;
					
					// Get the counter name.
					Integer counterId = pmis.getId().getCounterId();
					String fullCounterName = getCounterName(counterId);
					
					// Get the performance value
					Object perf = pmis.getValue()[pmis.getValue().length - 1];
					
					perfMap.put(fullCounterName, perf);
				}
			}
		}
		return perfMap;
	}

	/**
	 * Get the supply chain for this probe.
	 * 
	 * Buying / Selling relationship between service entities:
	 * 		Data centers sell commodities to hosts.
	 * 		Hosts sell commodities to virtual machines.
	 * 
	 * @return A set of template DTOs for this probe.
	 */
	@Override
	protected Set<TemplateDTO> getSupplyChainDefinition() {
		logger.info(LOGPREFIX + "Get supply chain");

		float used = 1f;
		float capacity = 100f;
		// Data Center
		CommodityDTO powerCommSoldPm = new CommodityDTO(Commodity.Power, null, used, capacity);
		CommodityDTO spaceCommSoldPm = new CommodityDTO(Commodity.Space, null, used, capacity);
		CommodityDTO coolingCommSoldPm = new CommodityDTO(Commodity.Cooling, null, used, capacity);
		TemplateDTO dc = new TemplateDTO(Entity.DataCenter, Lists.newArrayList(powerCommSoldPm, spaceCommSoldPm, coolingCommSoldPm), null, TemplateType.Base, 0);
		Provider dcProvider = new Provider(Entity.DataCenter, ProviderType.HOSTING, 1, 1);

		// Physical Machine
		CommodityDTO cpuCommSoldPm = new CommodityDTO(Commodity.CPU, null, used, capacity);
		CommodityDTO memCommSoldPm = new CommodityDTO(Commodity.Mem, null, used, capacity);
		CommodityDTO powerCommBoughtPmDc = new CommodityDTO(Commodity.Power, null, used, capacity);
		CommodityDTO spaceCommBoughtPmDc = new CommodityDTO(Commodity.Space, null, used, capacity);
		CommodityDTO coolingCommBoughtPmDc = new CommodityDTO(Commodity.Cooling, null, used, capacity);
		Map<Provider, List<CommodityDTO>> pmCommBoughtMap = TemplateDTO.createCommBoughtMap(dcProvider, powerCommBoughtPmDc);
		TemplateDTO.putCommBoughtInMap(dcProvider, spaceCommBoughtPmDc, pmCommBoughtMap);
		TemplateDTO.putCommBoughtInMap(dcProvider, coolingCommBoughtPmDc, pmCommBoughtMap);
		TemplateDTO pm = new TemplateDTO(Entity.PhysicalMachine,  Lists.newArrayList(cpuCommSoldPm, memCommSoldPm), pmCommBoughtMap, TemplateType.Base, 0);
		Provider pmProvider = new Provider(Entity.PhysicalMachine, ProviderType.HOSTING, 1, 1);

		// Virtual Machine
		CommodityDTO vcpuCommSoldVm = new CommodityDTO(Commodity.VCPU, null, used, capacity);
		CommodityDTO vmemCommSoldVm = new CommodityDTO(Commodity.VMem, null, used, capacity);
		CommodityDTO cpuCommBoughtVmPm = new CommodityDTO(Commodity.CPU, null, used, capacity);
		CommodityDTO memCommBoughtVmPm = new CommodityDTO(Commodity.Mem, null, used, capacity);
		Map<Provider, List<CommodityDTO>> vmCommBoughtMap = TemplateDTO.createCommBoughtMap(pmProvider, cpuCommBoughtVmPm);
		TemplateDTO.putCommBoughtInMap(pmProvider, memCommBoughtVmPm, vmCommBoughtMap);
		TemplateDTO vm = new TemplateDTO(Entity.VirtualMachine, Lists.newArrayList(vcpuCommSoldVm, vmemCommSoldVm), vmCommBoughtMap, TemplateType.Base, 0);
		return Sets.newHashSet(dc, pm, vm);
	}

	/**
	 * Return the fields and their meta data required by the probe to validate and discover the targets.
	 * 
	 * @return	Account Definition object
	 */
	@Override
	protected Map<String, AccountDefinitionEntry> getAccountDefinitionEntryMap() {
		logger.info(LOGPREFIX + "Get account definition");
		return accountDefinitionEntryMap;
	}

	/**
	 * Validate Target
	 * @param accountValues		Map representing the values for the fields in the AccountDefintion 
	 * 							required for validating the target
	 * @return					TargetValidationResponse
	 */
	@Override
	protected TargetValidationResponse validateTarget(Map<String, String> accountValues) {
		logger.info(LOGPREFIX +"Validate Target");
		TargetValidationResponse validationResponse = new TargetValidationResponse();
		if (connectVC(accountValues) != null) {
			validationResponse.targetValidationStatus = ResponseCode.SUCCESS;
		} else {
			validationResponse.targetValidationStatus = ResponseCode.FAIL;
			validationResponse.targetValidationExplanation = "Connection Failed";
		}
		return validationResponse;
	}
}