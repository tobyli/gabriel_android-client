package edu.cmu.cs.gabriel.aedassistant;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.text.format.Time;

/**
 * @author toby
 * @date 10/19/16
 * @time 6:39 PM
 */
public class MainController {
    public boolean active = false;
    private Object messageMutex = new Object();
    private State currentState;
    private TextToSpeech tts;
    private Time lastTtsTime = new Time();
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
        synchronized (messageMutex) {
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
                            //tts.stop();
                            //while (tts.isSpeaking()) {/* nothing to do. just wait*/}
                            //tts.speak(currentState.getPrompt(), TextToSpeech.QUEUE_FLUSH, null);

                            readOutCurrentPrompt(false);
                            break;
                        case StateMessage.ERROR_STATE:
                            moveToErrorState();
                            //tts.stop();
                            //while (tts.isSpeaking()) {/* nothing to do. just wait*/}
                            //tts.speak(currentState.getPrompt(), TextToSpeech.QUEUE_FLUSH, null);

                            readOutCurrentPrompt(false);
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

    public void readOutCurrentPrompt(boolean reread) throws Exception{
        if(currentState == null)
            throw new Exception("null current state");
        if(tts == null)
            throw new Exception("null tts");

        if (reread == true) {
            // check how long has it been since assistant last spoke
            Time now = new Time();
            now.setToNow();

            if (now.toMillis(false) - lastTtsTime.toMillis(false) < AEDAssistantConst.VOICE_PROMPT_DELAY) {
                return;
            }
        }
        tts.stop();
        //while (tts.isSpeaking()) {/* nothing to do. just wait*/}
        tts.speak(currentState.getPrompt(), TextToSpeech.QUEUE_FLUSH, null);
        lastTtsTime.setToNow();
    }

    public void init(){
        //create the states
        State initialState = new State("Initial state", "initialState", "AED Assistant ready. Say yes to continue.", State.INITIAL_STATE);

        State subjectAgeDetectionState = new State("Detecting if the human is an adult", "subjectAgeDetectionState", "", State.NORMAL_STATE);

        State adultDetectedState  = new State("Human detected as an adult", "adultDetectedState", "The subject seems to be an adult. Is this correct?", State.NORMAL_STATE);
        State childDetectedState = new State("Human detected as a child", "childDetectedState", "The subject seems to be a child. Is this correct?", State.NORMAL_STATE);

        State showAdultPadState = new State("State to detect adult pads", "showAdultPadState", "Please pick the pads with the red instructions and put them in front of the camera.", State.NORMAL_STATE);
        State showAdultPadErrorState = new State("Pads shown are not correct", "showAdultPadErrorState", "Those are the child pads. Please pick the pads with red instructions.", State.NORMAL_STATE);

        State showChildPadState = new State("State to detect child pads", "showChildPadState", "Please pick the pads with the blue instructions.", State.NORMAL_STATE);
        State showChildPadErrorState = new State("Pads shown are not correct", "showChildPadErrorState", "Those are the adult pads. Please pick the pads with blue instructions.", State.NORMAL_STATE);

        State peelInstructionsState = new State("State to peel instructions", "peelInstructionsState", "Great. Now peel the instructions from the pads. Say yes when ready.", State.NORMAL_STATE);

        State putPadOnLeftChestState = new State("State to put pad on left chest.", "putPadOnLeftChestState", "Good. Now, place the pad that goes to the left chest. Say yes when done.", State.NORMAL_STATE);
        State putPadOnLeftChestErrorState = new State("Left chest pad wrongly placed.", "putPadOnLeftChestErrorState", "Wrong location for the pad! Place the pad that goes to the left chest. Say yes when ready.", State.NORMAL_STATE);

        State putPadOnRightTorsoState =  new State("State to put pad on right torso.", "putPadOnRightTorsoState", "Ok. Now, place the pad that goes to the right torso. Say yes when done.", State.NORMAL_STATE);
        State putPadOnRightTorsoErrorState = new State("Right torso pad wrongly placed.", "putPadOnRightTorsoErrorState", "Wrong location for the pad! Please put the other pad on the right torso.", State.NORMAL_STATE);

        State finalState = new State("Final state", "finalState", "Good. You can now operate the AED machine.", State.ENDING_STATE);


        // Initial state
        initialState.setNextState(showAdultPadState);
        initialState.setErrorState(initialState);

        // Age detection
        subjectAgeDetectionState.setNextState(adultDetectedState);
        subjectAgeDetectionState.setErrorState(childDetectedState);

        //childDetectedState.setNextState(showChildPadState);
        //childDetectedState.setErrorState(subjectAgeDetectionState);

        //adultDetectedState.setNextState(showPadState);
        //adultDetectedState.setErrorState(subjectAgeDetectionState);

        // Pad showing
        showAdultPadState.setNextState(peelInstructionsState);
        showAdultPadState.setErrorState(showAdultPadErrorState);

        showAdultPadErrorState.setNextState(peelInstructionsState);
        showAdultPadErrorState.setErrorState(showAdultPadErrorState);

        // Instructions peeling
        peelInstructionsState.setNextState(putPadOnLeftChestState);
        peelInstructionsState.setErrorState(peelInstructionsState);

        //showChildPadState.setNextState(putPadOnLeftChestState);
        //showChildPadState.setErrorState(showChildPadErrorState);

        //showChildPadErrorState.setNextState(putPadOnLeftChestState);
        //showChildPadErrorState.setErrorState(showChildPadErrorState);

        // Putting pads
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
            readOutCurrentPrompt(false);
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
