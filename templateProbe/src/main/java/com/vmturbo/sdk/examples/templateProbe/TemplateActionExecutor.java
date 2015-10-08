package com.vmturbo.sdk.examples.templateProbe;

import java.util.Map;

import com.vmturbo.platform.common.dto.ActionResponseState;
import com.vmturbo.platform.sdk.common.DTO.ActionItemDTO;
import com.vmturbo.platform.sdk.probe.ActionResult;
import com.vmturbo.platform.sdk.probe.IActionExecutor;
import com.vmturbo.platform.sdk.probe.IProgressTracker;

/**
 * Template for SDK action executor.
 *
 */
public class TemplateActionExecutor implements IActionExecutor {

    /**
     * executor action based on received {@link ActionItemDTO}
     * @param actionItemDto: {@link ActionItemDTO} instance containing all information
     *                          necessary for execute the action
     * @param accountValuesMap: map containing credential information to access the target.
     *                          key is the credential name, value is the credential value.
     * @param progressTracker: action progress tracker
     * @return action execution result
     */
    @Override
    public ActionResult executeAction(ActionItemDTO actionItemDto, Map<String, String> accountValuesMap,
                    IProgressTracker progressTracker) {
        // implement action execution code here
        // need to notify server about action execution states:
        //      Update action execution progress:
        //      repeatedly check action execution state while action is being executed and
        //      call updateActionProgress to notify server about the action state.
        //      Maximum time interval between two calls of updateActionProgress should not exceed 30 seconds.
        return new ActionResult(ActionResponseState.SUCCEEDED, "Action execution has been succeeded");
    }

}
