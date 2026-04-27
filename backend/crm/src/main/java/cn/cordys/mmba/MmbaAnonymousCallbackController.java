package cn.cordys.mmba;

import cn.cordys.common.exception.GenericException;
import cn.cordys.mmba.callback.RedisStreamCallbackService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MMBA 平台回调入口（Shiro anon：/anonymous/**）。需 HTTP 200 且非空响应体表示成功。
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/anonymous/mmba")
public class MmbaAnonymousCallbackController {
    private final RedisStreamCallbackService streamCallbackService;

    public MmbaAnonymousCallbackController(RedisStreamCallbackService streamCallbackService) {
        this.streamCallbackService = streamCallbackService;
    }

    //数据回调规则说明：
    //1.回调地址对应的接口在接到数据后，如果返回http状态码为200且数据体不为空（具体内容可由业务方定义，方便排查问题即可），则视为数据接收成功，否则1分钟后才会推送新数据。所有数据以时间先后顺序排队回调，【重要】如果接口一直不通，则会阻塞后面的数据回调，并在本系统产生数据积压。接收方应考虑避免自身系统数据处理逻辑时间过长导致接口超时，进而阻塞数据回传。接收方应避免因自身业务逻辑处理单个记录未完成而拒接我方发送的数据，进而导致后续所有数据阻塞无法传输。
    //2.回调接口的连接超时时间为10秒，数据读取超时时间为20秒，建议异步处理回调数据。
    //3.接收成功的数据，会从推送通道中删除，不做留存
    //4.回调的data是一个列表，系统会将需要回调的数据放入回执通道，负责回调的定时任务会从通道中取出behaviorType相同的数据进行推送，一次最多回调20条数据
    //5.同一条记录有可能会回传多次，例如状态变化更新或是数据重推操作等，接收方应自行对数据重复性做校验，并对重复性数据做更新，一般以esId字段唯一标识记录，具体参考各个业务的接口说明。

    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "MMBA 审计/回执回调", hidden = true)
    public ResponseEntity<String> callback(@RequestBody JsonNode body) {
        try {

            streamCallbackService.callbackStream(body);

            return ResponseEntity.ok("ok");
        } catch (GenericException e) {
            log.error("MMBA 回调业务异常 body={}", body, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error");
        } catch (Exception e) {
            log.error("MMBA 回调系统异常 body={}", body, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error");
        }
    }
}
