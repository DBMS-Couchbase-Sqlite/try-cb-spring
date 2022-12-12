package trycb.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import trycb.model.Error;
import trycb.model.IValue;
import trycb.service.Hotel;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {
    private final Hotel hotelService;

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelController.class);
    private static final String LOG_FAILURE_MESSAGE = "Failed with exception";

    public HotelController(Hotel hotelService) {
        this.hotelService = hotelService;
    }

    @GetMapping(value = "/{description}/{location}/", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<? extends IValue> findHotelsByDescriptionAndLocation(@PathVariable("location") String location, @PathVariable("description") String desc) {
        try {
            return ResponseEntity.ok(hotelService.findHotels(location, desc));
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error(LOG_FAILURE_MESSAGE, e);
            return ResponseEntity.badRequest().body(new Error(e.getMessage()));
        }
    }

    @GetMapping(value = "/{description}/", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<? extends IValue> findHotelsByDescription(@PathVariable("description") String desc) {
        try {
            return ResponseEntity.ok(hotelService.findHotels(desc));
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error(LOG_FAILURE_MESSAGE, e);
            return ResponseEntity.badRequest().body(new Error(e.getMessage()));
        }
    }

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<? extends IValue> findAllHotels() {
        try {
            return ResponseEntity.ok(hotelService.findAllHotels());
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error(LOG_FAILURE_MESSAGE, e);
            return ResponseEntity.badRequest().body(new Error(e.getMessage()));
        }
    }
}
