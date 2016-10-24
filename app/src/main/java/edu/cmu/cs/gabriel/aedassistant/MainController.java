package edu.cmu.cs.gabriel.aedassistant;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

/**
 * @author toby
 * @date 10/19/16
 * @time 6:39 PM
 */
public class MainController {
    public boolean active = false;
    private State currentState;
    private TextToSpeech tts;
    private Context context;
    //TODO: the prompt of the current state should be read out every Ns

    public MainController(Context context){
        this.context = context;
        if (tts == null) {
            tts = new TextToSpeech(context, null);
        }
    }

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
            //correct identifier received
            try {
                switch (message.messageType) {
                    case StateMessage.NEXT_STATE:
                        moveToNextState();
                        tts.stop();
                        tts.speak(currentState.getPrompt(), TextToSpeech.QUEUE_FLUSH, null);
                        break;
                    case StateMessage.ERROR_STATE:
                        moveToErrorState();
                        tts.stop();
                        tts.speak(currentState.getPrompt(), TextToSpeech.QUEUE_FLUSH, null);
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

    public void readOutCurrentPrompt() throws Exception{
        if(currentState == null)
            throw new Exception("null current state");
        if(tts == null)
            throw new Exception("null tts");
        tts.speak(currentState.getPrompt(), TextToSpeech.QUEUE_FLUSH, null);
    }

    public void init(){
        //create the states
        State initialState = new State("Initial state for the script", "initialState", "Initial State", State.INITIAL_STATE);
        State showPadState = new State("Show the pad to the glass, is the pad shown red?", "showPadState", "Please pick the adult pad and hold the pad in front of you. Is the pad shown red?", State.NORMAL_STATE);
        State showPadErrorState = new State("Wrong pad shown to the glass", "showPadErrorState", "Wrong pad selected! Please pick the adult pad, which is the pad with read label. Is the pad shown red?", State.NORMAL_STATE);
        State putPadOnLeftChestState = new State("Put the pad on the face.", "putPadOnLeftChestState", "Please put the pad on the face of the dummy. Is the pad on the face now?", State.NORMAL_STATE);
        State putPadOnLeftChestErrorState = new State("Wrong location for the pad.", "putPadOnLeftChestErrorState", "Wrong location for the pad! Please put the pad on the face of the dummy. Is the pad on the face now?", State.NORMAL_STATE);
        State finalState = new State("Dummy is dead", "finalState", "This guy is dead. Mission completed!", State.ENDING_STATE);

        //linked the states together
        initialState.setNextState(showPadState);
        initialState.setErrorState(initialState);
        showPadState.setNextState(putPadOnLeftChestState);
        showPadState.setErrorState(showPadErrorState);
        showPadErrorState.setNextState(putPadOnLeftChestState);
        showPadErrorState.setErrorState(showPadErrorState);
        putPadOnLeftChestState.setNextState(finalState);
        putPadOnLeftChestState.setErrorState(putPadOnLeftChestErrorState);
        putPadOnLeftChestErrorState.setNextState(finalState);
        putPadOnLeftChestErrorState.setErrorState(putPadOnLeftChestErrorState);


        //use the first state as the current state
        currentState = initialState;
        try {
            readOutCurrentPrompt();
        }
        catch (Exception e){
            e.printStackTrace();
        }
        active = true;
    }

    public void destroy(){
        currentState = null;
    }




}
