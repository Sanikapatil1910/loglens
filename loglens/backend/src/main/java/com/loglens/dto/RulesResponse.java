package com.loglens.dto;

import com.loglens.model.RuleDescription;
import java.util.List;

public record RulesResponse(List<RuleDescription> rules, String disclaimer) {
}
