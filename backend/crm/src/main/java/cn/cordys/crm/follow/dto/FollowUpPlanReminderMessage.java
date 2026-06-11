package cn.cordys.crm.follow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpPlanReminderMessage implements Serializable {
    private String tenantId;
    private String organizationId;
    private String planId;
    private Long remindTime;
}
