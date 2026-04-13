package cn.cordys.crm.customer.controller;

import cn.cordys.crm.customer.dto.request.CustomerFailReasonAddRequest;
import cn.cordys.crm.customer.dto.request.CustomerFailReasonUpdateRequest;
import cn.cordys.crm.customer.dto.response.CustomerFailReasonResponse;
import cn.cordys.crm.customer.service.CustomerFailReasonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "客户失败原因配置")
@RestController
@RequestMapping("/customer/fail-reason")
@RequiredArgsConstructor
public class CustomerFailReasonController {

    private final CustomerFailReasonService customerFailReasonService;

    @Operation(summary = "获取失败原因列表")
    @GetMapping("/list")
    public List<CustomerFailReasonResponse> getList() {
        return customerFailReasonService.getList("100001");
    }

    @Operation(summary = "新增失败原因")
    @PostMapping
    public void add(@Valid @RequestBody CustomerFailReasonAddRequest request) {
        customerFailReasonService.add(request, "admin", "100001");
    }

    @Operation(summary = "更新失败原因")
    @PutMapping
    public void update(@Valid @RequestBody CustomerFailReasonUpdateRequest request) {
        customerFailReasonService.update(request, "admin", "100001");
    }

    @Operation(summary = "删除失败原因")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        customerFailReasonService.delete(id, "100001");
    }
}
