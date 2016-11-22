package edu.cmu.cs.gabriel.aedassistant;

import java.lang.reflect.Method;

/**
 * @author toby
 * @date 10/19/16
 * @time 6:31 PM
 */
public class State {
    public int stateType;
    public static final int INITIAL_STATE = 1, NORMAL_STATE = 2, ENDING_STATE = 3;
    private String description;
    private String identifier;
    private State nextState;
    private State errorState;
    private String prompt;
    private String disconnectedPrompt;

    public State(int stateType){
        super();
        this.stateType = stateType;
    }

    public State(String description, String identifier, String prompt, int stateType){
        super();
        this.description = description;
        this.identifier = identifier;
        this.prompt = prompt;
        this.disconnectedPrompt = prompt;
        this.stateType = stateType;
    }

    public void setDescription(String description){
        this.description = description;
    }

    public void setIdentifier(String identifier){
        this.identifier = identifier;
    }

    public void setPrompt(String prompt){
        this.prompt = prompt;
    }

    public void setNextState(State nextState){
        this.nextState = nextState;
    }

    public void setErrorState(State errorState){
        this.errorState = errorState;
    }

    public State getNextState(){
        return nextState;
    }

    public State getErrorState(){
        return errorState;
    }

    public String getDescription(){
        return description;
    }

    public String getIdentifier(){
        return identifier;
    }

    public String getPrompt(){
        return prompt;
    }

    public String getDisconnectedPrompt() {return  disconnectedPrompt;}

    public void setDisconnectedPrompt(String prompt){
        this.disconnectedPrompt = prompt;
    }

}
