import org.jclouds.aws.ec2.compute.AWSEC2TemplateOptions
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.jclouds.compute.domain.TemplateBuilder;

import com.nitorcreations.cloudyplugin.TemplateCustomizer;

class EC2Customizer implements TemplateCustomizer {
    public TemplateBuilder customize(TemplateBuilder templateBuilder) {
		Statement bootInstructions = AdminAccess.standard();
        templateBuilder.options(AWSEC2TemplateOptions.Builder.inboundPorts(22, 80, 8080).runScript(bootInstructions))
        return templateBuilder;
    }
}