package cn.cordys.crm.system.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PersonalWechatResponse {
    private boolean bound;
    private List<PersonalWechatItemResponse> wechats = new ArrayList<>();
}
