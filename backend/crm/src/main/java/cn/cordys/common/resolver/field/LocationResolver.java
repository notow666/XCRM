package cn.cordys.common.resolver.field;

import cn.cordys.common.utils.RegionUtils;
import cn.cordys.crm.system.dto.field.LocationField;

/**
 * @author song-cc-rock
 */
public class LocationResolver extends AbstractModuleFieldResolver<LocationField> {

    public static final String PCD = "PCD";
    public static final String PC = "PC";
    public static final String P = "P";
    public static final String C = "C";
    public static final String CHINA_PC = "CHINA_PC";

    @Override
    public void validate(LocationField customField, Object value) {

    }


    @Override
    public Object transformToValue(LocationField locationField, String value) {
        return RegionUtils.codeToName(value, locationField.getLocationType());
    }

    @Override
    public Object textToValue(LocationField field, String text) {
        return RegionUtils.mapping(text, true, field.getLocationType());
    }
}
