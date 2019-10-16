package io.quarkus.aws.lambda.runtime.model;

/***
 * Context passed by ALB proxy events
 */
public class AlbContext {
    private String targetGroupArn;

    public String getTargetGroupArn() {
        return targetGroupArn;
    }

    public void setTargetGroupArn(String targetGroupArn) {
        this.targetGroupArn = targetGroupArn;
    }
}
