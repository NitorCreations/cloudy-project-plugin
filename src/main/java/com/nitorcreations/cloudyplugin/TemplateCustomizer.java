package com.nitorcreations.cloudyplugin;

import org.jclouds.compute.domain.TemplateBuilder;

public interface TemplateCustomizer {
    TemplateBuilder customize(TemplateBuilder builder);
}
