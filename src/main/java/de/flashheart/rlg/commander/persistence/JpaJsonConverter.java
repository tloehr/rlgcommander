package de.flashheart.rlg.commander.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.json.JSONObject;

@Converter(autoApply = true)
public class JpaJsonConverter implements AttributeConverter<JSONObject, String> {
    @Override
    public String convertToDatabaseColumn(JSONObject jsonObject) {
        return jsonObject.toString();
    }

    @Override
    public JSONObject convertToEntityAttribute(String s) {
        return new JSONObject(s);
    }
}
