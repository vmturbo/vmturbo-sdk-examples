package com.vmturbo.sdk.examples.vimProbe;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URL;
import java.rmi.RemoteException;
import java.util.Map;

import org.apache.log4j.Logger;

import akka.japi.Pair;

import com.vmware.vim25.InvalidPowerState;
import com.vmware.vim25.ObjectSpec;
import com.vmware.vim25.ObjectUpdate;
import com.vmware.vim25.PropertyChange;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.PropertyFilterUpdate;
import com.vmware.vim25.PropertySpec;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.UpdateSet;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachineMovePriority;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.WaitOptions;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.PropertyCollector;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.util.PropertyCollectorUtil;

import com.vmturbo.platform.common.dto.ActionResponseState;
import com.vmturbo.platform.common.dto.ActionType;
import com.vmturbo.platform.common.dto.CommodityDTO;
import com.vmturbo.platform.common.dto.ModelEnum.CommodityAttribute;
import com.vmturbo.platform.sdk.common.DTO.ActionItemDTO;
import com.vmturbo.platform.sdk.common.DTO.EntityDTO;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntry;
import com.vmturbo.platform.sdk.common.util.ActionResponsePair;
import com.vmturbo.platform.sdk.probe.ActionResult;
import com.vmturbo.platform.sdk.probe.IActionExecutor;
import com.vmturbo.platform.sdk.probe.IProgressTracker;

/**
 * An implementation of Action Executor for SDK Sample Vim probe.
 * Specify this class in probe-conf.xml of the sample VimProbe.
 *
 */
public class VimActionExecutor implements IActionExecutor {

    private static final Logger logger = Logger.getLogger("com.vmturbo.platform.container.mediation");

    /**
     * These two variables are used by {@code terminateAction} when sending final action response.
     * They are set in function {@code finalizeResponse}
     */
    private ActionResponseState finalActionState;
    private String finalDescription;

    /**
     * If non-null, the service instance (VCenter) that will be the target of actions.
     *
     * if null, there is currently no logged-in service instance/
     */
    private ServiceInstance servInst = null;

    /**
     * VIM Task, if any, to run at the end of the action.
     */
    private Task task = null;

    //description for not implemented response
    private static final String notImplementedDesc = "NOT IMPLEMENTED";
    /**
     * Base time to sleep after an "odd" failure before trying again, in milliseconds. On the n'th
     * try, we will sleep n times this long - so the total time until we give up is
     * {@code
     * (maxOddFailureTries*(maxOddFailureTries+11)) / 2)*oddFailureSleep_ms
     * } milliseconds. (With the values as initially set, that works out to 27.5 seconds.)
     */
    private static final long oddFailureSleep_ms = 500;

    /**
     * Maximum number of times we try a remote action if it keeps failing in some "impossible" way.
     * This is a protection against looping when the remote results don't match expected behavior.
     */
    private static final int maxOddFailureTries = 10;

    // Max number of times we try to get the statue of a task
    private static final int maxTryGetTaskUpdate = 3;

    private static final int VM_PROGRESS_SHUTDOWN = 20;
    private static final int VM_PROGRESS_RECONFIG = 50;
    private static final int VM_PROGRESS_START = 30;

    /**
     * Implement the abstract method. This method is to execute Vim probe actions.
     */
    @Override
    public ActionResult executeAction(ActionItemDTO actionItem, Map<String, String> accountValues,
                    IProgressTracker progressTracker) {
        // Switch on service entity type
        switch(actionItem.getTargetSE().getEntity()){
            case VirtualMachine:
                executeVMTask(actionItem,accountValues, progressTracker);
                break;
            default:
                finalizeResponse(ActionResponseState.FAILED, notImplementedDesc);
                break;
        }

        // Monitor the Task object in the VC target for the action
        // progressRange means the partition of the current task in the progress of the whole action.
        int progressRange = 100 - actionItem.getProgress();
        progressRange = progressRange == 0 ? 100 : progressRange;

        ActionResponsePair actionResult = monitorTask(task, actionItem, progressRange, 60,
                        progressTracker);
        finalizeResponse(actionResult.getActionResponseState(), actionResult.getDescription());

        // Logout Server Connection
        if(servInst!=null){
            servInst.getServerConnection().logout();
        }

        // Ready to terminate the action.
        return new ActionResult(finalActionState, finalDescription);
    }



    /**
     * Monitoring the VC task states and return progress information
     * If the state is in progress, then progress message will be sent back to server
     * Otherwise, the result(Succeed/Failed) will be returned in a {@link Pair} object.
     * @param task: the {@link Task} to monitor
     * @param actionItem: the {@link ActionItemDTO} based on which the task is running
     * @param taskProgressRange: the partition of this task in progress of total action execution,
     *                          if the total progress of the whole action is 100
     * @param timeout: timeout for the task, in SECONDS
     * @return: {@link Pair}, the first element is {@link ActionResponseState}, the second element is a description for
     *          for the action state.
     */
    private ActionResponsePair monitorTask(Task vcTask, ActionItemDTO actionItem,
                    int taskProgressRange, int timeout, IProgressTracker progressTracker) {
        if (vcTask != null){
            String targetSEName = actionItem.getTargetSE().getDisplayName();

            // logger.info(task.getTaskInfo()+" task is on. "+"The task target is " + targetName);
            TaskInfoState state = TaskInfoState.error;
            // interval (in second) to check the task status
            int interval = 5;
            // number of times have been tried to get task status
            int count =0;

            // get start point of the action progress
            int startProgress = actionItem.getProgress();

            // total percentage for the current task in progress of whole action
            double fraction = taskProgressRange/100;

            while (timeout-- > 0) { //todo: add some timeout to exit the loop
                sleep(interval * 1000);
                if (servInst == null) {
                    logger.error("Don't have a logged-in connection to VC");
                    finalizeResponse(ActionResponseState.FAILED, ("Don't have a logged-in connection to VC"));
                }
                try {
                    state = (TaskInfoState)vcTask.getPropertyByPath("info.state");
                    String responseMessage;
                    int actionProgress;

                    switch (state) {
                        case queued:
                            actionItem.setProgress(startProgress);
                            progressTracker.updateActionProgress(
                                            ActionResponseState.QUEUED,
                                            String.format("%s: %s in status %s", targetSEName,
                                                            actionItem.getActionType(), state) + startProgress
                                                            + "%");
                            break;
                        case success:
                            actionProgress = startProgress + taskProgressRange;
                            actionItem.setProgress(actionProgress);
                            responseMessage = String.format("%s: %s in status %s", targetSEName, actionItem.getActionType(),state) + " - 100%";
                            return new ActionResponsePair(ActionResponseState.SUCCEEDED, responseMessage);
                        case running:
                            Object progress = task.getPropertyByPath("info.progress");
                            actionProgress = ((Double)((Integer)progress * fraction)).intValue() + startProgress;
                            actionItem.setProgress(actionProgress);
                            progressTracker.updateActionProgress(ActionResponseState.IN_PROGRESS,
                                            String.format("%s: %s in status %s - %s", targetSEName,
                                                            actionItem.getActionType(), state,
                                                            actionProgress + "%"));
                            break;
                        default:
                            responseMessage = String.format("%s: %s in status %s", targetSEName,actionItem.getActionType(),state);
                            return new ActionResponsePair(ActionResponseState.FAILED, responseMessage);

                    }
                    // After getting the task status, set it to zero.
                    // The count get incremented when there are exceptions
                    count = 0;
                }
                catch (Exception e) {
                    if(++count >= maxTryGetTaskUpdate){
                        logger.error("Exception caught during monitoring task: ", e);
                        finalizeResponse(ActionResponseState.FAILED, e.getMessage());
                        return makeResponsePair(ActionResponseState.FAILED, e.getMessage());
                    }
                }
            }
        }
        return makeResponsePair(ActionResponseState.FAILED, "Cannot find the valid VC Task");
    }

    private ActionResponsePair makeResponsePair(ActionResponseState state, String responseMessage) {
        return new ActionResponsePair(state, responseMessage);
    }

    /**
     * Execute tasks for Virtual Machine entities.
     * @param actionItem    {@link ActionItemDTO} for the VM.
     * @param accountValues     Map of credentials to connect to VCenter.
     * @param progressTracker action progress tracker
     */
    private void executeVMTask(ActionItemDTO actionItem, Map<String, String> accountValues,
                    IProgressTracker progressTracker) {
        String vmName = actionItem.getTargetSE().getDisplayName();
        logger.info("running executeVMTask for vmName: "+ vmName);
        // Connect to VC.
        connectVC(actionItem,accountValues);
        if(servInst==null) {
            return;
        }

        // Get handle to the VM object in the VCenter
        VirtualMachine vm = null;
        try {
            vm = findManagedEntity(com.vmware.vim25.mo.VirtualMachine.class.getSimpleName(), vmName);
            if (vm == null){
                logger.error("Can not find VM  : " + vmName);
                finalizeResponse(ActionResponseState.FAILED, ("NOT Found VM  : " + vmName));
                return;
            }
        }
        catch (RemoteException e) {
            logger.error("Exception during executing VM Task: ", e);
            finalizeResponse(ActionResponseState.FAILED, e.getMessage());
            return;
        }
        // Switch based on action type.
        switch(actionItem.getActionType()){
            case START:
                vmStart(vm, actionItem);
                break;
            case MOVE:
                vmMove(vm, actionItem);
                break;
            case RECONFIGURE:
                vmReconfigure(vm, actionItem, progressTracker);
                break;
            case RIGHT_SIZE:
                vmRightSize(vm, actionItem, progressTracker);
            default:
                // send not implemented error message
                finalizeResponse(ActionResponseState.FAILED, notImplementedDesc);
                break;
        }
    }

    /**
     * Start(Power on) a VM.
     * @param vm The Virtual Machine to be started (powered on).
     * @param actionItem    {@link ActionItemDTO} for the VM.
     */
    private void vmStart(VirtualMachine vm, ActionItemDTO actionItem) {
        checkNotNull(vm);
        String hostName = actionItem.getHostedBySE().getDisplayName();
        // Get handle to the host of the VM.
        HostSystem host = null;
        try {
            host = findManagedEntity(com.vmware.vim25.mo.HostSystem.class.getSimpleName(), hostName);
        }
        catch (RemoteException e) {
            logger.error("Remote exception during start VM " + vm.getName(), e);
            finalizeResponse(ActionResponseState.FAILED, e.getMessage());
            return;
        }
        if (host == null){
            logger.error("Did not find host  : " + hostName);
            finalizeResponse(ActionResponseState.FAILED, ("NOT Found PM  : " + hostName));
            return;
        }

        // Try for few times
        for (int tries = 1; tries <= maxOddFailureTries; tries++) {
            // If the VM is already powered on, we're done.
            if (vmPowerState(vm) == VirtualMachinePowerState.poweredOn) {
                logger.info(vm.getName() + " is already powered on");
                finalizeResponse(ActionResponseState.SUCCEEDED, (vm.getName() + " is already powered on"));
                return;
            }

            try {
                // This is the task object that will be monitored for action progress
                task = vm.powerOnVM_Task(host);
                logger.info(vm.getName() + " power on task is on");
                // The task to start the VM has been started and will be monitored in the executeAction method
                return;
            } catch (InvalidPowerState e) {
                logger.warn(vm.getName() + " invalid power state " + vmPowerState(vm));
                // If the VM somehow powered on, we'll get this exception.
                // Go back around and check again.
                sleep(tries * oddFailureSleep_ms);
                continue;
            } catch (Exception e) {
                logger.error("Exception during start VM " + vm.getName(), e);
                finalizeResponse(ActionResponseState.FAILED, e.getMessage());
                return;
            }
        }
        // if you came here means you could not execute action, task object is null.
        // Thus executeVMTask will send action failure response.
    }

    /**
     * Move a VM from one host to the other host.
     * @param vm The Virtual Machine to be moved.
     * @param actionItem    {@link ActionItemDTO} for the VM.
     */
    private void vmMove(VirtualMachine vm,ActionItemDTO actionItem)  {
        if ((Boolean)vm.getPropertyByPath("summary.config.template")) {
            finalizeResponse(ActionResponseState.FAILED, String.format(vm.getName(),
                                                                       "is a template and can't be moved"));
            return;
        }
        // Get the handle for the host that the VM will be moved to.
        String newHostName = actionItem.getNewSE().getDisplayName();
        HostSystem targetHost = null;
        try {
            targetHost = findManagedEntity(com.vmware.vim25.mo.HostSystem.class.getSimpleName(), newHostName);
        }
        catch (RemoteException e) {
            logger.error("Remote exception while finding target host ", e);
            finalizeResponse(ActionResponseState.FAILED, e.getMessage());
            return;
        }
        // TODO need to check all datastores of target VM are accessible on the target host where VM is to be moved to

        // Get VM resource poll
        ResourcePool vmRp = null;
        try {
            vmRp = vm.getResourcePool();
        }
        catch (RemoteException e) {
            logger.error("Remote exception during finding resource pool ", e);
            finalizeResponse(ActionResponseState.FAILED, e.getMessage());
            return;
        }

        VirtualMachinePowerState state = vm.getRuntime().getPowerState();

        try {
            task = vm.migrateVM_Task(vmRp, targetHost, VirtualMachineMovePriority.defaultPriority, state);
        }
        catch ( RemoteException e) {
            logger.error("Remote exception when move VM " + vm.getName(), e);
            finalizeResponse(ActionResponseState.FAILED, e.getMessage());
            return;
        }
    }


    /**
     * Change the number of VCPUs a virtual machine uses.
     * First shutdown the VM through guest OS. Wait for the VM power status change to powered off.
     * Then do CPU reconfiguration task. Wait for the task to finish.
     * Power on the VM. Power on task will be traced by executeAction method.
     * @param vm The Virtual Machine whose VCPU count is to be changed
     * @param actionItem    {@link ActionItemDTO} for the VM.
     */
    private void vmReconfigure(VirtualMachine vm, ActionItemDTO actionItem,
                    IProgressTracker progressTracker) {
        vmRightSizeCpu(vm, actionItem, true, progressTracker);
    }

    /**
     * change number of VCPU and capacity of Memory for the VM
     * @param vm: the VM to resize
     * @param actionItem: {@link ActionItemDTO} which contains necessary information for resizing
     * @param isCpu: notifying if the method is caled to change number of VCPUs or memory capacity
     */
    private void vmRightSizeCpu(VirtualMachine vm, ActionItemDTO actionItem, boolean isCpu,
                    IProgressTracker progressTracker) {
        if (isCpu) {
            int numCpu = (int)actionItem.getNewComm().getCapacity();
            VirtualMachineConfigSpec spec = new VirtualMachineConfigSpec();
            spec.setNumCPUs(numCpu);
            // Need to shutdown VM first before doing reconfigure.
            try {
                // Send progress message while checking power state loop
                vmShutdownGuest(vm, actionItem, progressTracker);
                actionItem.setProgress(VM_PROGRESS_SHUTDOWN);

                task = vm.reconfigVM_Task(spec);
                ActionResponsePair responsePair = monitorTask(task, actionItem,
                                VM_PROGRESS_RECONFIG, 60, progressTracker);
                if(responsePair.getActionResponseState() == ActionResponseState.FAILED) {
                    finalizeResponse(responsePair.getActionResponseState(), responsePair.getDescription());
                    return;
                }
            }
            catch (RemoteException e) {
                logger.error("Remote exception right size CPU for VM " + vm.getName(), e);
                finalizeResponse(ActionResponseState.FAILED, e.getMessage());
                return;
            }

            // This function will change task to point to vmStart task.
            vmStart(vm, actionItem);
            ActionResponsePair responsePair = monitorTask(task, actionItem, VM_PROGRESS_START, 60,
                            progressTracker);
            if(responsePair.getActionResponseState() == ActionResponseState.FAILED) {
                finalizeResponse(responsePair.getActionResponseState(), responsePair.getDescription());
                return;
            }
        }
    }

    /**
     * Rightsize VM by changing its commodity capacity or limit.
     * Triggered by {@link ActionType} MOVE action.
     * @param vm
     * @param actionItemDto
     */
    private void vmRightSize(VirtualMachine vm, ActionItemDTO actionItemDto,
                    IProgressTracker progressTracker) {
        EntityDTO newSE = actionItemDto.getNewSE();
        CommodityDTO newComm = actionItemDto.getNewComm();

        if (newSE != null) {
            logger.error("VM RightSize action does not require EntityDTO information");

        }

        // Get CommodityAttribute field from ActionItemDTO
        CommodityAttribute attrType = actionItemDto.getCommodityAttribute();

        if (newComm != null) {
            if (attrType == CommodityAttribute.Capacity) {
                vmRightSizeCpu(vm, actionItemDto, true, progressTracker);
            }
            else if (attrType == CommodityAttribute.Limit) {
                // TODO(tian): implement it later after test capacity
            } else {
                logger.error("Unhandled Attribute Type " + attrType.toString()
                                 + " for VM RightSize on VM " + vm.getName());
            }
        }
    }

    /**
     * Shut down the virtual machine using the guest OS. This fails if the VM is suspended or is not
     * running, or if it isn't running the VMware guest tools.
     * @param vm The Virtual Machine whose guest is to be shut down.
     * @param actionItem    {@link ActionItemDTO} for the VM.
     */
    private void vmShutdownGuest(VirtualMachine vm, ActionItemDTO actionItem,
                    IProgressTracker progressTracker) {
        // TODO deal with the situation where VM is already powered off or suspended
        checkNotNull(vm);
        try {
            vm.shutdownGuest();
        }
        catch (RemoteException e) {
            logger.error("Exception when shutdown guest for VM " + vm.getName(), e);
            finalizeResponse(ActionResponseState.FAILED, e.getMessage());
            return;
        }
        // We can not trace task status here, because once the shutdown task is initiated the task
        // status will be updated to succeed. So we need to check if the VM has been poweredOff.
        try {
            waitForPowerState(vm, VirtualMachinePowerState.poweredOff, actionItem, progressTracker);
        }
        catch (RemoteException e) {
            logger.error("Remote exception when wait for power state change for VM " + vm.getName(), e);
            finalizeResponse(ActionResponseState.FAILED, e.getMessage());
            return;
        }
    }

    /**
     * Initialize a connection to the VCenter that the target service entity is associated with.
     * On normal return, {@code servInst} -  which must initially be null - is the logged in
     * connection and is non-null. Otherwise {@code servInst} remains null and
     * @param actionItem    {@link ActionItemDTO} for the VM.
     * @param accountValues     Map of credentials to connect to VCenter.
     */
    protected void connectVC(ActionItemDTO actionItem,Map<String, String> accountValues) {
        final String targetAddr = accountValues.get(AccountDefinitionEntry.NAME_OR_ADDRESS_FIELD);
        final String username = accountValues.get(AccountDefinitionEntry.USERNAME_FIELD);
        final String password = accountValues.get(AccountDefinitionEntry.PASSWORD_FIELD);
        final String url = "https://" + targetAddr + "/sdk";
        int timeout = 60 * 1000; // 60sec timeout for connection
        String namespace = ServiceInstance.VIM25_NAMESPACE;

        try {
            servInst = new ServiceInstance(new URL(url), username, password, true, namespace);
            servInst.getServerConnection().getVimService().getWsc().setConnectTimeout(timeout);
        }
        catch (Exception e) {
            logger.error("Exception when connect to VC ", e);
            finalizeResponse(ActionResponseState.FAILED, e.getMessage());
        }

        if (servInst == null){
            logger.error("Cannot Connected to VC : " + targetAddr);
            finalizeResponse(ActionResponseState.FAILED, ("Cannot Connected to VC : " + targetAddr));
        }
    }

    /**
     * Find the managed entity named {@code name}, which is assumed to have class {@code clazz}.
     * @param clazz Class object for the returned entity
     * @param name Name of the object to find
     * @return The object found, null otherwise
     * @throws RemoteException it will occur if there is a problem with that communication
     */
    private <T> T findManagedEntity(String clazz, String name)
                    throws RemoteException {
        InventoryNavigator invNav = new InventoryNavigator(servInst.getRootFolder());

        @SuppressWarnings("unchecked")
        T result = (T)invNav.searchManagedEntity(clazz, name);
        return result;
    }

    /**
     * Get the power state of the virtual machine.
     *
     * @param vm The Virtual Machine to get power state.
     * @return An enum value describing the virtual machine's power state.
     */
    private VirtualMachinePowerState vmPowerState(VirtualMachine vm) {
        checkNotNull(vm);

        return (VirtualMachinePowerState)vm.getPropertyByPath("runtime.powerState");
    }

    /**
     * Wait until a "machine" is in the given power state.
     * @param m The machine
     * @param state State to wait for
     * @throws RemoteException if there is a communications problem
     */
    // TODO send heart beat messages
    private <M extends ManagedEntity, S> void waitForPowerState(M m, S state,
                    ActionItemDTO actionItem, IProgressTracker progressTracker)
                throws RemoteException {
        checkNotNull(m);
        checkNotNull(state);
        PropertyCollector propCol = servInst.getPropertyCollector();
        PropertySpec pSpec = PropertyCollectorUtil
                        .createPropertySpec(m.getClass().getSimpleName(), false,
                                            new String[] {"runtime.powerState"});
        ObjectSpec oss = new ObjectSpec();
        oss.setObj(m.getMOR());
        PropertyFilterSpec pfs = new PropertyFilterSpec();
        pfs.setObjectSet(new ObjectSpec[] {oss});
        pfs.setPropSet(new PropertySpec[] {pSpec});
        propCol.createFilter(pfs, false);
        S curState = null;
        String version = "";

        while (!state.equals(curState)) {
            progressTracker.updateActionProgress(ActionResponseState.IN_PROGRESS,
                            "Action in progress - " + actionItem.getProgress() + "%");

            WaitOptions options = new WaitOptions();
            UpdateSet update = propCol.waitForUpdatesEx(version, options);
            try {
                PropertyFilterUpdate pfu = update.getFilterSet()[0];
                ObjectUpdate ou = pfu.getObjectSet()[0];
                PropertyChange pch = ou.getChangeSet()[0];
                @SuppressWarnings("unchecked")
                S val = (S)pch.getVal();
                curState = val;
                if (state.equals(curState)) {
                    actionItem.setProgress(actionItem.getProgress() + VM_PROGRESS_SHUTDOWN);
                }
            }
            catch (NullPointerException | ArrayIndexOutOfBoundsException e) {
                logger.error("Exception caught during waitting for power state: ", e);
            }
            finally {
                version = update.getVersion();
            }
        }
    }

    /**
     * Helper function to send either succeed or fail response. Need to disconnect from VC before
     * sending out the response. Because otherwise, the actor will be killed and have no chance to
     * disconnect.
     * @param actionState Value of ActionResponseState for the final response.
     * @param description A string the describes detail of the response.
     * is responding to
     */
    private void finalizeResponse(ActionResponseState actionState, String description){
         finalActionState = actionState;
         finalDescription = description;
    }

    /**
     * Sleep and discard {@code InterruptedException}. This little function also avoids writing
     * pointless try/empty catch blocks all over the place.
     * @param millis Number of milliseconds to sleep.
     */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            logger.error("Exception during thread sleep", e);
        }
    }

}//end class
