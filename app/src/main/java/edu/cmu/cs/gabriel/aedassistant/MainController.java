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
        if(!message.getStateIdentifier().contentEquals(currentState.getIdentifier())) {
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
        State initialState = new State("Initial state for the script", "initialState", "Initial State. Please say Yes to continue.", State.INITIAL_STATE);

        State subjectAgeDetectionState = new State("Detecting if the human is an adult", "subjectAgeDetectionState", "Is the human in front of you an adult?", State.NORMAL_STATE);
        State adultDetectedState  = new State("Human detected as a child", "adultDetectedState", "The human in front of you is identified as a child, is that correct?", State.NORMAL_STATE);
        State childDetectedState = new State("Human detected as a adult", "childDetectedState", "The human in front of you is identified as an adult, is that correct?", State.NORMAL_STATE);


        State showPadState = new State("Show the pad to the glass, is the pad shown red?", "showPadState", "Please pick the adult pad and hold the pad in front of you. Is the pad shown red?", State.NORMAL_STATE);
        State showPadErrorState = new State("Wrong pad shown to the glass", "showPadErrorState", "Wrong pad selected! Please pick the adult pad, which is the pad with red label. Is the pad shown red?", State.NORMAL_STATE);

        State showChildPadState = new State("Show the pad to the glass, is the pad shown blue?", "showChildPadState", "Please pick the child pad and hold the pad in front of you. Is the pad shown blue?", State.NORMAL_STATE);
        State showChildPadErrorState = new State("Wrong pad shown to the glass", "showChildPadErrorState", "Wrong pad selected! Please pick the child pad, which is the pad with blue label. Is the pad shown blue?", State.NORMAL_STATE);

        State putPadOnLeftChestState = new State("Put the pad on the left chest of the dummy.", "putPadOnLeftChestState", "Please put the pad on the left chest of the dummy. Is the pad on the left chest now?", State.NORMAL_STATE);
        State putPadOnLeftChestErrorState = new State("Wrong location for the pad.", "putPadOnLeftChestErrorState", "Wrong location for the pad! Please put the pad on the left chest of the dummy. Is the pad on the left chest now?", State.NORMAL_STATE);
        State putPadOnRightTorsoState =  new State("Put the other pad on the right torso of the dummy.", "putPadOnRightTorsoState", "Please put the other pad on the right torso of the dummy. Is the pad on the right torso now?", State.NORMAL_STATE);
        State putPadOnRightTorsoErrorState = new State("Wrong location for the pad.", "putPadOnRightTorsoErrorState", "Wrong location for the pad! Please put the other pad on the right torso of the dummy. Is the pad on the right torso now?", State.NORMAL_STATE);
        State finalState = new State("Dummy is dead", "finalState", "This guy is dead. Mission completed!", State.ENDING_STATE);


        //linked the states together
        initialState.setNextState(subjectAgeDetectionState);
        initialState.setErrorState(initialState);
        subjectAgeDetectionState.setNextState(adultDetectedState);
        subjectAgeDetectionState.setErrorState(childDetectedState);

        childDetectedState.setNextState(showChildPadState);
        childDetectedState.setErrorState(subjectAgeDetectionState);

        adultDetectedState.setNextState(showPadState);
        adultDetectedState.setErrorState(subjectAgeDetectionState);

        showPadState.setNextState(putPadOnLeftChestState);
        showPadState.setErrorState(showPadErrorState);

        showPadErrorState.setNextState(putPadOnLeftChestState);
        showPadErrorState.setErrorState(showPadErrorState);

        showChildPadState.setNextState(putPadOnLeftChestState);
        showChildPadState.setErrorState(showChildPadErrorState);

        showChildPadErrorState.setNextState(putPadOnLeftChestState);
        showChildPadErrorState.setErrorState(showChildPadErrorState);

        putPadOnLeftChestState.setNextState(putPadOnRightTorsoState);
        putPadOnLeftChestState.setErrorState(putPadOnLeftChestErrorState);

        putPadOnLeftChestErrorState.setNextState(putPadOnRightTorsoState);
        putPadOnLeftChestErrorState.setErrorState(putPadOnLeftChestErrorState);

        putPadOnRightTorsoState.setNextState(finalState);
        putPadOnRightTorsoState.setErrorState(putPadOnRightTorsoErrorState);

        putPadOnRightTorsoErrorState.setNextState(finalState);
        putPadOnRightTorsoErrorState.setErrorState(putPadOnRightTorsoErrorState);

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
