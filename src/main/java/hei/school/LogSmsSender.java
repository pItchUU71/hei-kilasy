package hei.school;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LogSmsSender implements SmsSender {

    @Value("${kilasy.sms.fail:false}")
    private boolean fail;

    @Override
    public void send(String phoneNumber, String message) {
        if (fail) {
            throw new IllegalStateException("Panne simulée du fournisseur SMS");
        }
        System.out.println("[SMS] " + phoneNumber + " : " + message);
    }
}
