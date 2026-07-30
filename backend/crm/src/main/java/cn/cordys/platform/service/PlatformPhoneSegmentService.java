package cn.cordys.platform.service;

import cn.cordys.common.pager.Pager;
import cn.cordys.platform.dto.request.PlatformPhoneSegmentPageRequest;
import cn.cordys.platform.dto.response.PhoneSegmentRegionNodeResponse;
import cn.cordys.platform.dto.response.PlatformPhoneSegmentGroupResponse;
import cn.cordys.platform.dto.response.PlatformPhoneSegmentResponse;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PlatformPhoneSegmentService {

    private static final String CACHE_PHONE_SEGMENT_REGIONS = "phone_segment_regions";

    @Resource
    @Qualifier("masterJdbcTemplate")
    private JdbcTemplate masterJdbcTemplate;

    public Pager<List<PlatformPhoneSegmentResponse>> page(PlatformPhoneSegmentPageRequest request) {
        int current = Math.max(1, request.getCurrent());
        int pageSize = Math.max(1, request.getPageSize());
        int offset = (current - 1) * pageSize;
        String province = StringUtils.trimToEmpty(request.getProvince());
        String city = StringUtils.trimToEmpty(request.getCity());

        Long total = masterJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM platform_phone_segment WHERE (? = '' OR province = ?) AND (? = '' OR city = ?)",
                Long.class, province, province, city, city);

        String sql = "SELECT id, province, city, segment, prefix, isp, area_code " +
                "FROM platform_phone_segment WHERE (? = '' OR province = ?) AND (? = '' OR city = ?) " +
                "ORDER BY province, city, segment LIMIT ? OFFSET ?";
        List<PlatformPhoneSegmentResponse> list = masterJdbcTemplate.query(sql, (rs, rowNum) -> mapSegmentRow(rs),
                province, province, city, city, pageSize, offset);

        return new Pager<>(list, total == null ? 0L : total, pageSize, current);
    }

    @Cacheable(cacheNames = CACHE_PHONE_SEGMENT_REGIONS, key = "'all'", unless = "#result == null")
    public List<PhoneSegmentRegionNodeResponse> listRegions() {
        List<Map<String, Object>> rows = masterJdbcTemplate.queryForList(
                "SELECT DISTINCT province, city FROM platform_phone_segment ORDER BY province, city");
        Map<String, Set<String>> provinceCityMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String province = String.valueOf(row.get("province"));
            String city = String.valueOf(row.get("city"));
            provinceCityMap.computeIfAbsent(province, key -> new LinkedHashSet<>()).add(city);
        }
        List<PhoneSegmentRegionNodeResponse> result = new ArrayList<>();
        provinceCityMap.forEach((province, cities) -> {
            PhoneSegmentRegionNodeResponse provinceNode = new PhoneSegmentRegionNodeResponse();
            provinceNode.setLabel(province);
            provinceNode.setValue(province);
            List<PhoneSegmentRegionNodeResponse> children = new ArrayList<>();
            for (String city : cities) {
                PhoneSegmentRegionNodeResponse cityNode = new PhoneSegmentRegionNodeResponse();
                cityNode.setLabel(city);
                cityNode.setValue(city);
                children.add(cityNode);
            }
            provinceNode.setChildren(children);
            result.add(provinceNode);
        });
        return result;
    }

    public List<PlatformPhoneSegmentResponse> listByProvinceCity(String province, String city) {
        return listByProvinceCity(province, city, null);
    }

    public List<PlatformPhoneSegmentResponse> listByProvinceCity(String province, String city, String prefix) {
        String normalizedProvince = StringUtils.trimToEmpty(province);
        String normalizedCity = StringUtils.trimToEmpty(city);
        String normalizedPrefix = StringUtils.trimToEmpty(prefix);
        StringBuilder sql = new StringBuilder(
                "SELECT id, province, city, segment, prefix, isp, area_code " +
                        "FROM platform_phone_segment WHERE province = ? AND city = ?");
        List<Object> params = new ArrayList<>();
        params.add(normalizedProvince);
        params.add(normalizedCity);
        if (StringUtils.isNotBlank(normalizedPrefix)) {
            sql.append(" AND prefix = ?");
            params.add(normalizedPrefix);
        }
        sql.append(" ORDER BY segment");
        return masterJdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapSegmentRow(rs), params.toArray());
    }

    public List<PlatformPhoneSegmentGroupResponse> listSegmentGroups(String province, String city) {
        String normalizedProvince = StringUtils.trimToEmpty(province);
        String normalizedCity = StringUtils.trimToEmpty(city);
        String sql = "SELECT prefix, COUNT(1) AS cnt " +
                "FROM platform_phone_segment WHERE province = ? AND city = ? " +
                "GROUP BY prefix ORDER BY prefix";
        return masterJdbcTemplate.query(sql, (rs, rowNum) -> {
            PlatformPhoneSegmentGroupResponse group = new PlatformPhoneSegmentGroupResponse();
            group.setPrefix(rs.getString("prefix"));
            group.setCount(rs.getLong("cnt"));
            return group;
        }, normalizedProvince, normalizedCity);
    }

    public long countByProvinceCity(String province, String city) {
        String normalizedProvince = StringUtils.trimToEmpty(province);
        String normalizedCity = StringUtils.trimToEmpty(city);
        Long total = masterJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM platform_phone_segment WHERE province = ? AND city = ?",
                Long.class, normalizedProvince, normalizedCity);
        return total == null ? 0L : total;
    }

    private PlatformPhoneSegmentResponse mapSegmentRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        PlatformPhoneSegmentResponse item = new PlatformPhoneSegmentResponse();
        item.setId(rs.getString("id"));
        item.setProvince(rs.getString("province"));
        item.setCity(rs.getString("city"));
        item.setSegment(rs.getString("segment"));
        item.setPrefix(rs.getString("prefix"));
        item.setIsp(rs.getString("isp"));
        item.setAreaCode(rs.getString("area_code"));
        return item;
    }

    public List<PlatformPhoneSegmentResponse> listByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        String sql = "SELECT id, province, city, segment, prefix, isp, area_code " +
                "FROM platform_phone_segment WHERE id IN (" + placeholders + ")";
        return masterJdbcTemplate.query(sql, (rs, rowNum) -> mapSegmentRow(rs), ids.toArray());
    }
}
