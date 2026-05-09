package cn.cordys.mmba.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.TimeUtils;
import cn.cordys.common.util.Translator;
import cn.cordys.excel.domain.ExcelErrData;
import cn.cordys.mmba.domain.MmbaDevice;
import cn.cordys.mmba.dto.response.MmbaDeviceImportResponse;
import cn.cordys.mmba.excel.MmbaDeviceImportDict;
import cn.cordys.mmba.excel.MmbaDeviceImportRow;
import cn.idev.excel.EasyExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * MMBA 设备 Excel 导入（与导出模板列头一致）。
 */
@Slf4j
@Service
public class MmbaDeviceImportService {

    @Resource
    private MmbaDeviceService mmbaDeviceService;

    public MmbaDeviceImportResponse importExcel(MultipartFile file, String userId) {
        if (file == null || file.isEmpty()) {
            throw new GenericException(Translator.get("file_cannot_be_null"));
        }
        String name = file.getOriginalFilename();
        if (name == null || !StringUtils.endsWithIgnoreCase(name, ".xlsx")) {
            throw new GenericException(Translator.get("mmba_device_import_invalid_file"));
        }
        DeviceImportListener listener = new DeviceImportListener(mmbaDeviceService, userId);
        try {
            EasyExcel.read(file.getInputStream(), MmbaDeviceImportRow.class, listener).sheet().doRead();
        } catch (IOException e) {
            log.error("mmba device import read error", e);
            throw new GenericException(Translator.get("mmba_device_import_error"));
        }
        MmbaDeviceImportResponse response = new MmbaDeviceImportResponse();
        response.setSuccessCount(listener.successCount);
        response.setFailCount(listener.errList.size());
        response.setErrorMessages(listener.errList);
        return response;
    }

    private static final class DeviceImportListener extends AnalysisEventListener<MmbaDeviceImportRow> {

        private final MmbaDeviceService deviceService;
        private final String userId;
        private final List<ExcelErrData> errList = new ArrayList<>();
        private int successCount;

        private DeviceImportListener(MmbaDeviceService deviceService, String userId) {
            this.deviceService = deviceService;
            this.userId = userId;
        }

        @Override
        public void invoke(MmbaDeviceImportRow row, AnalysisContext context) {
            int rowNum = context.readRowHolder().getRowIndex() + 1;
            try {
                if (isBlankRow(row)) {
                    return;
                }
                MmbaDevice device = toDevice(row);
                deviceService.saveOrUpdateDevice(device, userId);
                successCount++;
            } catch (Exception e) {
                log.warn("mmba device import row {} failed: {}", rowNum, e.getMessage());
                errList.add(new ExcelErrData(rowNum, e.getMessage() != null ? e.getMessage() : "error"));
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            // no-op
        }

        private static boolean isBlankRow(MmbaDeviceImportRow row) {
            if (row == null) {
                return true;
            }
            return StringUtils.isAllBlank(
                    row.getUm(),
                    row.getStaffName(),
                    row.getOrgName(),
                    row.getUmStatus());
        }

        private static MmbaDevice toDevice(MmbaDeviceImportRow row) {
            MmbaDevice d = new MmbaDevice();
            d.setId(StringUtils.trimToNull(row.getUm()));
            d.setStaffName(StringUtils.trimToNull(row.getStaffName()));
            d.setOrgName(StringUtils.trimToNull(row.getOrgName()));
            d.setEnable("启用".equals(StringUtils.trimToNull(row.getUmStatus())));
            return d;
        }

        /**
         * 单元格含英文逗号时拆成两段：第一段→phone，第二段→phone2；无逗号则仅写入 phone。
         */
        private static void applyPhoneFromImportCell(String raw, MmbaDevice d) {
            if (StringUtils.isBlank(raw)) {
                return;
            }
            String trimmed = raw.trim();
            int comma = trimmed.indexOf(',');
            if (comma < 0) {
                d.setPhone(normalizePhone(trimmed));
                return;
            }
            String first = trimmed.substring(0, comma).trim();
            String second = trimmed.substring(comma + 1).trim();
            d.setPhone(normalizePhone(first));
            d.setPhone2(normalizePhone(second));
        }

        /**
         * 单元格含英文逗号时拆成两段：第一段→telecomOperators，第二段→telecomOperators2；无逗号则仅写入前者。
         */
        private static void applyTelecomOperatorsFromImportCell(String raw, MmbaDevice d) {
            if (StringUtils.isBlank(raw)) {
                return;
            }
            String trimmed = raw.trim();
            int comma = trimmed.indexOf(',');
            if (comma < 0) {
                d.setTelecomOperators(StringUtils.trimToNull(trimmed));
                return;
            }
            String first = trimmed.substring(0, comma).trim();
            String second = trimmed.substring(comma + 1).trim();
            d.setTelecomOperators(StringUtils.trimToNull(first));
            d.setTelecomOperators2(StringUtils.trimToNull(second));
        }

        /**
         * 单元格含英文逗号时拆成两段：第一段→iccid，第二段→iccid2；无逗号则仅写入 iccid。
         */
        private static void applyIccidFromImportCell(String raw, MmbaDevice d) {
            if (StringUtils.isBlank(raw)) {
                return;
            }
            String trimmed = raw.trim();
            int comma = trimmed.indexOf(',');
            if (comma < 0) {
                d.setIccid(StringUtils.trimToNull(trimmed));
                return;
            }
            String first = trimmed.substring(0, comma).trim();
            String second = trimmed.substring(comma + 1).trim();
            d.setIccid(StringUtils.trimToNull(first));
            d.setIccid2(StringUtils.trimToNull(second));
        }

        private static String normalizePhone(String raw) {
            if (raw == null) {
                return null;
            }
            String s = raw.trim();
            if (s.startsWith("+86")) {
                s = s.substring(3).trim();
            }
            return StringUtils.isBlank(s) ? null : s;
        }
    }
}
