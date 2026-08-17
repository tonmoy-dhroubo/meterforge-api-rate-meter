package io.meterforge.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component("meterForgeGatewayProperties")
@ConfigurationProperties(prefix = "meterforge.gateway")
public class MeterForgeGatewayProperties {

    private String apiKeyPepper = "dev-secret-pepper-change-in-production-12345678";
    private String instanceId = "gateway-1";
    private String usageTopic = "meterforge.usage.v1";

    public String getApiKeyPepper() {
        return apiKeyPepper;
    }

    public void setApiKeyPepper(String apiKeyPepper) {
        this.apiKeyPepper = apiKeyPepper;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getUsageTopic() {
        return usageTopic;
    }

    public void setUsageTopic(String usageTopic) {
        this.usageTopic = usageTopic;
    }
}
