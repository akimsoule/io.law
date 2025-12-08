package bj.gouv.sgg.service;

import bj.gouv.sgg.exception.PromptLoadException;

public interface LoadPrompt {

    String loadPrompt(String filename) throws PromptLoadException;

}
