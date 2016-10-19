package edu.cmu.cs.gabriel.aedassistant;

import java.util.Calendar;

/**
 * @author toby
 * @date 10/19/16
 * @time 6:54 PM
 */
public class StateMessage {
    private long timeStamp;
    private String stateIdentifier;
    public int messageType;
    final static public int NEXT_STATE = 1, ERROR_STATE = 2, HOLD = 3;

    public StateMessage(String stateIdentifier, int messageType){
        this.timeStamp = Calendar.getInstance().getTimeInMillis();
        this.stateIdentifier = stateIdentifier;
        this.messageType = messageType;
    }

    public StateMessage(long timeStamp, String stateIdentifier, int messageType){
        this.timeStamp = timeStamp;
        this.stateIdentifier = stateIdentifier;
        this.messageType = messageType;
    }

    public String getStateIdentifier(){
        return stateIdentifier;
    }

}
