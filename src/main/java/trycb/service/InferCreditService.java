package trycb.service;

import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class InferCreditService {
    public long inferCreditForUser() {
        int low = 2000;
        int high = 3000;
        return new Random().nextInt(high-low) + (long) low;
    }
}
