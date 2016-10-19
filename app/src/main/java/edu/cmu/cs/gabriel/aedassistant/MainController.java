package edu.cmu.cs.gabriel.aedassistant;

import android.util.Log;

/**
 * @author toby
 * @date 10/19/16
 * @time 6:39 PM
 */
public class MainController {
    private State currentState;


    public State getCurrentState(){
        return currentState;
    }

    //TODO: state message should be priority-queued by time stamp
    public void handleStateMessage(StateMessage message){
        if(message.getStateIdentifier() != currentState.getIdentifier()) {
            //wrong identifier received
            Log.w("CONTROLLER", "Wrong identifier received, received " + message.getStateIdentifier() + " expecting " + currentState.getIdentifier());
            //do nothing
        }
        else{
            try {
                switch (message.messageType) {
                    case StateMessage.NEXT_STATE:
                        moveToNextState();
                        break;
                    case StateMessage.ERROR_STATE:
                        moveToErrorState();
                        break;
                    case StateMessage.HOLD:
                    default:
                        //do nothing
                }
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }

    }

    private void moveToNextState() throws Exception{
        if(currentState.getNextState() == null)
            throw currentState.stateType == State.ENDING_STATE ? new Exception("Reach the ending state") : new Exception("NULL next state");
        else
            currentState = currentState.getNextState();
    }

    private void moveToErrorState() throws Exception{
        if(currentState.getErrorState() == null)
            throw currentState.stateType == State.ENDING_STATE ? new Exception("Reach the ending state") : new Exception("NULL next state");
        else
            currentState = currentState.getErrorState();
    }




}
