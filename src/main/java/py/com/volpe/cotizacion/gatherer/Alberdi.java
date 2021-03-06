package py.com.volpe.cotizacion.gatherer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import py.com.volpe.cotizacion.AppException;
import py.com.volpe.cotizacion.domain.Place;
import py.com.volpe.cotizacion.domain.PlaceBranch;
import py.com.volpe.cotizacion.domain.QueryResponse;
import py.com.volpe.cotizacion.domain.QueryResponseDetail;
import py.com.volpe.cotizacion.repository.PlaceRepository;
import py.com.volpe.cotizacion.repository.QueryResponseRepository;

import javax.transaction.Transactional;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author Arturo Volpe
 * @since 4/26/18
 */
@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class Alberdi implements Gatherer {

    private static final String CODE = "ALBERDI";
    private static final String WS_URL = "ws://cambiosalberdi.com:9300";
    private final PlaceRepository placeRepository;
    private final QueryResponseRepository queryResponseRepository;

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public List<QueryResponse> doQuery() {

        Place p = addOrUpdatePlace();


        try {
            Map<String, List<ExchangeData>> result = getParsedData();

            return p.getBranches().stream().map(b -> {
                QueryResponse qr = QueryResponse.builder().branch(b).date(new Date()).place(b.getPlace()).build();
                List<ExchangeData> data = result.get(b.getRemoteCode());

                qr.setDetails(data.stream().map(detail -> {

                    String iso = mapToISO(detail);
                    if (iso == null) return null;

                    QueryResponseDetail qrd = new QueryResponseDetail();
                    qrd.setQueryResponse(qr);
                    qrd.setIsoCode(iso);
                    qrd.setSalePrice(Long.parseLong(detail.getVenta().replace(".", "")));
                    qrd.setPurchasePrice(Long.parseLong(detail.getCompra().replace(".", "")));

                    return qrd;
                }).filter(Objects::nonNull).collect(Collectors.toList()));


                return queryResponseRepository.save(qr);
            }).collect(Collectors.toList());


        } catch (IOException e) {
            throw new AppException(500, "cant parse the result of ALBERDI ws", e);
        }

    }

    @Override
    public Optional<Place> get() {
        return placeRepository.findPlaceByCode(CODE);
    }

    @Override
    public Place addOrUpdatePlace() {
        return get().orElseGet(this::create);
    }


    private Place create() {


        log.info("Creating place alberdi");
        Place p = Place.builder().name("Cambios Alberdi").code(CODE).branches(new ArrayList<>()).build();

        try {
            Map<String, List<ExchangeData>> result = getParsedData();

            result.keySet().forEach(name -> p.getBranches().add(buildBranch(name, name, p)));

            return placeRepository.save(p);
        } catch (IOException e) {
            throw new AppException(500, "cant parse the result of alberdi ws", e);
        }

    }

    private PlaceBranch buildBranch(String name, String code, Place p) {
        PlaceBranch.PlaceBranchBuilder pb = PlaceBranch.builder()
                .name(name)
                .remoteCode(code)
                .place(p);

        switch (name) {
            case "asuncion":
                return pb.name("Asunción")
                        .latitude(-25.281411)
                        .longitude(-57.6375917)
                        .image("https://lh5.googleusercontent.com/p/AF1QipMxA2Nv-mtjAzqti1pUgd_Bt3z8nfbBBizklGEw=w408-h244-k-no")
                        .phoneNumber("(021) 447.003 / (021) 447.004")
                        .schedule("07:45 horas a 17:00 horas de Lunes a Viernes, 07:45 horas a 12:00 horas Sábados")
                        .email("matriz@cambiosalberdi.com")
                        .build();
            case "villamorra":
                return pb.name("Villa Morra")
                        .phoneNumber("(021) 609.905 / (021) 609.906")
                        .schedule("08:00 horas a 17:00 horas de Lunes a Viernes, 08:00 horas a 12:00 horas Sábados")
                        .image("https://lh5.googleusercontent.com/p/AF1QipP9gq7gRfgXTFGdQFGJYWLZGq_9SSLJ_pKYN4Uk=w408-h306-k-no")
                        .latitude(-25.2962143)
                        .longitude(-57.5766948)
                        .build();
            case "sanlo":
                return pb.name("San Lorenzo")
                        .image("https://lh5.googleusercontent.com/p/AF1QipM0yx3fvWQArt0kY6EwyaaAsgX1jYRy3OuIobjr=w408-h725-k-no")
                        .latitude(-25.3459184)
                        .longitude(-57.5151255)
                        .phoneNumber("Teléfonos: (021) 571.215 / (021) 571.216")
                        .schedule("08:00 horas a 17:00 horas de Lunes a Viernes, 08:00 horas a 12:00 horas Sábados")
                        .build();
            case "salto":
                return pb.name("SALTO DEL GUAIRÁ")
                        .latitude(-24.055276)
                        .longitude(-54.3246485)
                        .phoneNumber("Teléfonos: (046) 243.158 / (046) 243.159")
                        .schedule("08:00 horas a 16:00 horas de Lunes a Viernes, 07:30 horas a 11:30 horas Sábados")
                        .build();
            case "cde":
                return pb.name("SUCURSAL 1 CDE")
                        .latitude(-25.5098204)
                        .longitude(-54.6164127)
                        .phoneNumber("Teléfonos: (061) 500.135 / (061) 500.417")
                        .schedule("07:00 horas a 17:00 horas de Lunes a Viernes, 07:00 horas a 12:00 horas Sábados")
                        .build();
            case "cde2":
                return pb.name("CDE KM 4")
                        .latitude(-25.5095271)
                        .longitude(-54.6485326)
                        .phoneNumber("Teléfonos: (061) 571.540 / (061) 571.536")
                        .schedule("07:00 horas a 17:00 horas de Lunes a Viernes, 07:00 horas a 12:00 horas Sábados")
                        .build();
            case "enc":
                return pb.name("ENCARNACIÓN")
                        .latitude(-27.3314553)
                        .longitude(-55.8670186)
                        .image("https://lh5.googleusercontent.com/p/AF1QipOAtjZef_kGv14qJ4h68Rt4CKOxxwYXPJW30BUY=w408-h306-k-no")
                        .schedule("07:45 horas a 17:00 horas de Lunes a Viernes, 07:45 horas a 12:00 horas Sábados")
                        .phoneNumber("Teléfonos: (071) 205.154 / (071) 205.120 / (071) 205.144")
                        .build();
            default:
                log.warn("Unkonw branch {} of alberdi", name);
                return pb.build();

        }
    }

    private Map<String, List<ExchangeData>> getParsedData() throws IOException {
        return buildMapper().readValue(getData(), new TypeReference<Map<String, List<ExchangeData>>>() {
        });
    }


    private ObjectMapper buildMapper() {
        return new ObjectMapper();
    }

    /**
     * This method is overly complicated
     * <p>
     * First we create a {@link StandardWebSocketClient}, and attach it to a manager along with a
     * handler, in this case an {@link AbstractWebSocketHandler} and we wait for the handleTextMessage for the
     * real message.
     * <p>
     * Obviously this API is created to be used in more complex scenarios, in this case we only need one message,
     * the message is automatically send (we don't need to ask for it).
     * <p>
     * The API is prepared to be used in an async environment, we make it 'sync' here using the wait/notify java
     * API.
     *
     * @return the json that the web service return
     */
    protected static String getData() {

        try {
            Object monitor = new Object();
            AtomicReference<String> val = new AtomicReference<>();

            StandardWebSocketClient swsc = new StandardWebSocketClient();
            WebSocketConnectionManager manager = new WebSocketConnectionManager(swsc, new AbstractWebSocketHandler() {
                @Override
                protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                    val.set(message.getPayload());
                    session.close();
                    synchronized (monitor) {
                        monitor.notifyAll();
                    }
                }
            }, WS_URL);
            manager.setAutoStartup(true);
            manager.start();
            int retries = 4;
            String result = null;
            while (val.get() == null && retries-- > 0) {
                synchronized (monitor) {
                    monitor.wait(10000);
                    result = val.get();
                }
            }
            manager.stop();
            return result;
        } catch (Exception e) {
            throw new AppException(500, "can't connect to socket alberdi", e);
        }
    }

    private static String mapToISO(ExchangeData data) {
        // We don't care for the check exchange
        if (data.moneda.contains("Cheque")) return null;
        switch (data.img) {
            case "dolar.png":
                return "USD";
            case "real.png":
                return "BRL";
            case "euro.png":
                return "EUR";
            case "peso.png":
                return "ARS";
            default:
                return null;
        }
    }

    @Data
    private static class ExchangeData {
        String moneda;
        String img;
        String compra;
        String venta;
    }

}
