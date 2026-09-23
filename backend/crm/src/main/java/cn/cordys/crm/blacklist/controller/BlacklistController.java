package cn.cordys.crm.blacklist.controller;

import cn.cordys.crm.blacklist.service.BlacklistService;
import cn.cordys.security.SessionUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/blacklist")
public class BlacklistController {
    @Resource private BlacklistService service;
    public record PageRequest(String keyword, int current, int pageSize) { }
    public record AddRequest(String mobile, String customerName) { }
    public record DeleteRequest(List<String> ids) { }
    public record DeleteByConditionRequest(String keyword) { }
    public record ExportRequest(String keyword, List<String> ids) { }

    @PostMapping("/page")
    @RequiresPermissions("BLACKLIST:READ")
    public Map<String, Object> page(@RequestBody PageRequest request) {
        return service.page(request.keyword(), request.current(), request.pageSize());
    }

    @PostMapping("/add")
    @RequiresPermissions("BLACKLIST:ADD")
    public Map<String, Object> add(@RequestBody AddRequest request) {
        return service.add(SessionUtils.getUserId(), request.mobile(), request.customerName());
    }

    @PostMapping("/batch-delete")
    @RequiresPermissions("BLACKLIST:DELETE")
    public Map<String, Object> delete(@RequestBody DeleteRequest request) {
        return service.delete(SessionUtils.getUserId(), request.ids());
    }

    @PostMapping("/delete-by-condition")
    @RequiresPermissions("BLACKLIST:DELETE")
    public Map<String, Object> deleteByCondition(@RequestBody DeleteByConditionRequest request) {
        return service.deleteByCondition(SessionUtils.getUserId(), request.keyword());
    }

    @PostMapping("/import")
    @RequiresPermissions("BLACKLIST:IMPORT")
    public Map<String, Object> importFile(@RequestPart("file") MultipartFile file) {
        return service.importFile(SessionUtils.getUserId(), file);
    }

    @PostMapping("/export")
    @RequiresPermissions("BLACKLIST:EXPORT")
    public void export(@RequestBody ExportRequest request, HttpServletResponse response) throws IOException {
        download(response, service.exportFile(SessionUtils.getUserId(), request.keyword(), request.ids()), "黑名单.xlsx");
    }

    @GetMapping("/template")
    @RequiresPermissions("BLACKLIST:IMPORT")
    public void template(HttpServletResponse response) throws IOException {
        download(response, service.template(), "黑名单导入模板.xlsx");
    }

    @GetMapping("/import-error/{id}")
    @RequiresPermissions("BLACKLIST:IMPORT")
    public void importError(@PathVariable("id") String id, HttpServletResponse response) throws IOException {
        byte[] file = service.importErrorFile(SessionUtils.getUserId(), id);
        boolean legacyExcel = file.length >= 2 && (file[0] & 0xff) == 0xd0 && (file[1] & 0xff) == 0xcf;
        download(response, file, legacyExcel ? "黑名单导入错误.xls" : "黑名单导入错误.xlsx");
    }
    private void download(HttpServletResponse response, byte[] bytes, String name) throws IOException {
        response.setContentType(name.endsWith(".xls") ? "application/vnd.ms-excel"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(name, StandardCharsets.UTF_8));
        response.getOutputStream().write(bytes);
    }
}
