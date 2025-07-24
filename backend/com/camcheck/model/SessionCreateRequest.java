package com.camcheck.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class SessionCreateRequest {
    private String name;
    private Map<String, Object> settings = new HashMap<>();
} 