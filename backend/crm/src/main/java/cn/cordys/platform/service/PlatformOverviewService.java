package cn.cordys.platform.service;

import cn.cordys.platform.dto.response.PlatformOverviewResponse;
import cn.cordys.platform.dto.response.PlatformOverviewSeriesItem;
import cn.cordys.platform.dto.response.PlatformTenantItemResponse;
import cn.cordys.tenant.mapper.ExtTenantMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PlatformOverviewService {

    private static final long CACHE_TTL_MS = 30_000L;
    private static final int TENANT_ONLINE_TOP_N = 10;
    private static final String SERIES_OTHER = "OTHER";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_FROZEN = "FROZEN";
    private static final String COVERAGE_WITH_ONLINE = "WITH_ONLINE";
    private static final String COVERAGE_WITHOUT_ONLINE = "WITHOUT_ONLINE";

    @Resource
    private ExtTenantMapper extTenantMapper;

    @Resource
    private PlatformSessionOverviewService platformSessionOverviewService;

    private volatile long cacheExpireAt;
    private volatile PlatformOverviewResponse cached;

    public PlatformOverviewResponse getOverview() {
        long now = System.currentTimeMillis();
        PlatformOverviewResponse hit = cached;
        if (hit != null && now < cacheExpireAt) {
            return hit;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            hit = cached;
            if (hit != null && now < cacheExpireAt) {
                return hit;
            }
            PlatformOverviewResponse built = buildOverview();
            cached = built;
            cacheExpireAt = now + CACHE_TTL_MS;
            return built;
        }
    }

    public void invalidateOverviewCache() {
        cached = null;
        cacheExpireAt = 0L;
    }

    private PlatformOverviewResponse buildOverview() {
        PlatformOverviewResponse response = new PlatformOverviewResponse();

        long active = 0L;
        long frozen = 0L;
        List<PlatformOverviewSeriesItem> tenantStatusSeries = new ArrayList<>();
        List<Map<String, Object>> statusRows = extTenantMapper.countGroupByStatus();
        if (statusRows != null) {
            for (Map<String, Object> row : statusRows) {
                String status = String.valueOf(row.get("status"));
                long cnt = toLong(row.get("cnt"));
                if (STATUS_ACTIVE.equalsIgnoreCase(status)) {
                    active = cnt;
                } else if (STATUS_FROZEN.equalsIgnoreCase(status)) {
                    frozen = cnt;
                }
                tenantStatusSeries.add(new PlatformOverviewSeriesItem(displayTenantStatus(status), cnt));
            }
        }
        response.setTenantActive(active);
        response.setTenantFrozen(frozen);
        response.setTenantTotal(active + frozen);
        response.setTenantStatusSeries(tenantStatusSeries);

        PlatformSessionOverviewService.SessionOnlineSnapshot online =
                platformSessionOverviewService.scanOnlineSessions();
        response.setOnlineUserTotal(online.getOnlineUserTotal());
        response.setOnlineTenantUserTotal(online.getOnlineTenantUserTotal());
        response.setOnlineMultiDeviceUserCount(online.getOnlineMultiDeviceUserCount());
        response.setOnlinePlatformUserCount(online.getOnlinePlatformUserCount());
        response.setOnlineDataSpecialistUserCount(online.getOnlineDataSpecialistUserCount());

        Map<String, String> tenantLabelById = buildTenantLabelMap();
        response.setOnlineByTenantSeries(buildOnlineByTenantSeries(online.getLocalPrincipalsByTenant(), tenantLabelById));
        response.setActiveTenantOnlineCoverageSeries(
                buildActiveTenantCoverageSeries(extTenantMapper.listActiveTenantIds(), online.getLocalPrincipalsByTenant()));

        return response;
    }

    private Map<String, String> buildTenantLabelMap() {
        Map<String, String> map = new HashMap<>();
        List<PlatformTenantItemResponse> briefs = extTenantMapper.listTenantBrief();
        if (briefs == null) {
            return map;
        }
        for (PlatformTenantItemResponse item : briefs) {
            if (item == null || StringUtils.isBlank(item.getTenantId())) {
                continue;
            }
            String label = StringUtils.trimToNull(item.getName());
            if (label == null) {
                label = StringUtils.trimToNull(item.getCode());
            }
            if (label == null) {
                label = item.getTenantId();
            }
            map.put(item.getTenantId(), label);
        }
        return map;
    }

    private List<PlatformOverviewSeriesItem> buildOnlineByTenantSeries(
            Map<String, Set<String>> localPrincipalsByTenant,
            Map<String, String> tenantLabelById) {
        List<PlatformOverviewSeriesItem> ranked = new ArrayList<>();
        if (localPrincipalsByTenant == null || localPrincipalsByTenant.isEmpty()) {
            return ranked;
        }
        for (Map.Entry<String, Set<String>> entry : localPrincipalsByTenant.entrySet()) {
            String tenantId = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue().size();
            if (count <= 0) {
                continue;
            }
            String label = tenantLabelById.getOrDefault(tenantId, tenantId);
            ranked.add(new PlatformOverviewSeriesItem(label, (long) count));
        }
        ranked.sort(Comparator.comparing(PlatformOverviewSeriesItem::getValue).reversed());

        List<PlatformOverviewSeriesItem> result = new ArrayList<>();
        long otherSum = 0L;
        for (int i = 0; i < ranked.size(); i++) {
            if (i < TENANT_ONLINE_TOP_N) {
                result.add(ranked.get(i));
            } else {
                otherSum += ranked.get(i).getValue();
            }
        }
        if (otherSum > 0) {
            result.add(new PlatformOverviewSeriesItem(SERIES_OTHER, otherSum));
        }
        return result;
    }

    private List<PlatformOverviewSeriesItem> buildActiveTenantCoverageSeries(
            List<String> activeTenantIds,
            Map<String, Set<String>> localPrincipalsByTenant) {
        long withOnline = 0L;
        long withoutOnline = 0L;
        if (activeTenantIds != null) {
            for (String tenantId : activeTenantIds) {
                Set<String> principals = localPrincipalsByTenant == null ? null : localPrincipalsByTenant.get(tenantId);
                if (principals != null && !principals.isEmpty()) {
                    withOnline++;
                } else {
                    withoutOnline++;
                }
            }
        }
        List<PlatformOverviewSeriesItem> series = new ArrayList<>();
        series.add(new PlatformOverviewSeriesItem(COVERAGE_WITH_ONLINE, withOnline));
        series.add(new PlatformOverviewSeriesItem(COVERAGE_WITHOUT_ONLINE, withoutOnline));
        return series;
    }

    private static String displayTenantStatus(String status) {
        if (StringUtils.isBlank(status)) {
            return "UNKNOWN";
        }
        return status.toUpperCase(Locale.ROOT);
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
