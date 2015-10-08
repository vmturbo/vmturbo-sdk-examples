package com.vmturbo.sdk.examples.vimProbe;

import java.util.Map;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

import com.google.common.collect.Maps;
import com.vmturbo.platform.common.dto.ActionType;
import com.vmturbo.platform.common.dto.ModelEnum.Entity;
import com.vmturbo.platform.sdk.common.DTO.ActionItemDTO;
import com.vmturbo.platform.sdk.common.DTO.EntityDTO;
import com.vmturbo.platform.sdk.common.messages.Message;
import com.vmturbo.platform.sdk.common.util.AccountDefinitionEntry;

/**
 * This class is to test the functionality of actionExecutor before integration test.
 * The test will create a ActionRequest for executing VM actions on a VC target
 * and the action will be observed in the VSphere.
 *
 * It has a main method which initiates a VimActionExecutor actor and send message to that.
 * Note : When testing, we need to add the onReceive() function to VimActionExecutor.
 *
 */
public class VimActionExecutorTest {
    static ActorRef clientActor = null;
    // Credentials to connect to VCenter. accountValues map of ActionRequest Message
    private static final String vcIP = "10.10.150.203";
    private static final String vcLogin = "corp\\haoyuan.wang";
    private static final String vcPwd = "Sysdreamworks123";

    private static Map<String, String> accountValues = null;


    /**
     * Main function. Initiates VimActionExecutorTest and call test functions.
     * @param args No arguments needed
     */
    public static void main(String[] args){
        VimActionExecutorTest actor = new VimActionExecutorTest();
        actor.init();
        actor.testMove();

    }


    /**
     * Test vmStart method.
     */
    private void testStart(){
        // Construct actionItemDto of ActionRequest Message
        String targetSEName = "photon-02";
        EntityDTO targetSE = new EntityDTO(Entity.VirtualMachine, "");
        targetSE.setDisplayName(targetSEName);

        String hostSEName = "hp-esx28.corp.vmturbo.com";
        EntityDTO hostSE = new EntityDTO(Entity.PhysicalMachine, "");
        hostSE.setDisplayName(hostSEName);

        ActionItemDTO actionItemDto = new ActionItemDTO();
        actionItemDto.setTargetSE(targetSE);
        actionItemDto.setHostedBySE(hostSE);
        actionItemDto.setActionType(ActionType.START);

        Message.ActionRequest inMsg = Message.actionRequest(actionItemDto,"vim",accountValues,clientActor);
        clientActor.tell(inMsg,clientActor);
    }

    /**
     * Test vmMove method.
     */
    private void testMove(){
        // Construct actionItemDto of ActionRequest Message
        // VM that will be moved
        String targetSEName = "SUSE-1";
        EntityDTO targetSE = new EntityDTO(Entity.VirtualMachine, "");
        targetSE.setDisplayName(targetSEName);

        // Current host of the VM
        String hostSEName = "hp-esx21.corp.vmturbo.com";
        EntityDTO hostSE = new EntityDTO(Entity.PhysicalMachine, "");
        hostSE.setDisplayName(hostSEName);

        // Destination host of the VM
        String newHostSEName = "hp-esx24.corp.vmturbo.com";
        EntityDTO newSE = new EntityDTO(Entity.PhysicalMachine, "  ");
        newSE.setDisplayName(newHostSEName);

        ActionItemDTO actionItemDto = new ActionItemDTO();
        actionItemDto.setTargetSE(targetSE);
        actionItemDto.setHostedBySE(hostSE);
        actionItemDto.setNewSE(newSE);
        actionItemDto.setActionType(ActionType.MOVE);

        // send the action
        Message.ActionRequest inMsg = Message.actionRequest(actionItemDto,"vim",accountValues,clientActor);
        clientActor.tell(inMsg,clientActor);
    }

    /**
     * Creates an Actor System and create an VimActionExecutor actor.
     */
    private void init() {
        System.out.println("Started");
        final ActorSystem system = ActorSystem.create("simpleSystem");
        clientActor = system.actorOf(Props.create(VimActionExecutor.class), "clientActor");

        accountValues = Maps.newHashMap();
        accountValues.put(AccountDefinitionEntry.NAME_OR_ADDRESS_FIELD, vcIP);
        accountValues.put(AccountDefinitionEntry.USERNAME_FIELD, vcLogin);
        accountValues.put(AccountDefinitionEntry.PASSWORD_FIELD, vcPwd);
    }

}
