package cn.cordys.crm.customer.controller;

import cn.cordys.crm.customer.dto.request.CustomerFollowWayAddRequest;
import cn.cordys.crm.customer.dto.request.CustomerFollowWayUpdateRequest;
import cn.cordys.crm.customer.dto.response.CustomerFollowWayResponse;
import cn.cordys.crm.customer.service.CustomerFollowWayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "客户跟进方式配置")
@RestController
@RequestMapping("/customer/follow-way")
@RequiredArgsConstructor
public class CustomerFollowWayController {

    private final CustomerFollowWayService customerFollowWayService;

    @Operation(summary = "获取跟进方式列表")
    @GetMapping("/list")
    public List<CustomerFollowWayResponse> getList() {
        return customerFollowWayService.getList("100001");
    }

    @Operation(summary = "新增跟进方式")
    @PostMapping
    public void add(@Valid @RequestBody CustomerFollowWayAddRequest request) {
        customerFollowWayService.add(request, "admin", "100001");
    }

    @Operation(summary = "更新跟进方式")
    @PutMapping
    public void update(@Valid @RequestBody CustomerFollowWayUpdateRequest request) {
        customerFollowWayService.update(request, "admin", "100001");
    }

    @Operation(summary = "删除跟进方式")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        customerFollowWayService.delete(id, "100001");
    }
}
