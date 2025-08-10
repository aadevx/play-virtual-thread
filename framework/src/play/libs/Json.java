package play.libs;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import java.io.Reader;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Json Utility serialize & Deserialize
 * @author Arief Ardiyansah
 */
public class Json {

    private static final Gson GSON = new GsonBuilder().setDateFormat("MMM dd, yyyy h:mm:ss aaa")
            .registerTypeAdapter(Date.class, new GsonDateDeSerializer()).setLenient().serializeNulls()
            .create();

    public static <T> T fromJson(JsonElement json, Class<T> classOfT) {
        return GSON.fromJson(json, classOfT);
    }

    public static <T> T fromJson(JsonElement json, Type typeOfT) {
        return GSON.fromJson(json, typeOfT);
    }

    public static <T> T fromJson(JsonReader reader, Type typeOfT) {
        return GSON.fromJson(reader, typeOfT);
    }

    public static <T> T fromJson(Reader json, Class<T> classOfT) {
        return GSON.fromJson(json, classOfT);
    }

    public static <T> T fromJson(Reader json, Type typeOfT) {
        return GSON.fromJson(json, typeOfT);
    }

    public static <T> T fromJson(String json, Class<T> classOfT) {
        return GSON.fromJson(json, classOfT);
    }

    public static <T> T fromJson(String json, Type typeOfT) {
        return GSON.fromJson(json, typeOfT);
    }

    public static String toJson(JsonElement jsonElement) {
        return GSON.toJson(jsonElement);
    }

    public static String toJson(Object src) {
        return GSON.toJson(src);
    }

    public static String toJson(Object src, Type typeOfSrc) {
        return GSON.toJson(src, typeOfSrc);
    }

    public static JsonElement toJsonTree(Object src) {
        return GSON.toJsonTree(src);
    }

    public static JsonElement toJsonTree(Object src, Type typeOfSrc) {
        return GSON.toJsonTree(src, typeOfSrc);
    }

    public static boolean isJson(String content) {
        try {
           JsonParser.parseString(content);
           return true;
        }catch (Exception e) {
            return false;
        }
    }

    public static class GsonDateDeSerializer implements JsonDeserializer<Date> {

        private final SimpleDateFormat format1 = new SimpleDateFormat("MMM dd, yyyy h:mm:ss aaa");
        private final SimpleDateFormat format2 = new SimpleDateFormat("MMM dd, yyyy, h:mm:ss aaa");
        @Override
        public Date deserialize(JsonElement json, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            try {
                String j = json.getAsJsonPrimitive().getAsString();
                return parseDate(j);
            } catch (ParseException e) {
                throw new JsonParseException(e.getMessage(), e);
            }
        }

        private Date parseDate(String dateString) throws ParseException {
            if (dateString != null && !dateString.trim().isEmpty()) {
                try {
                    return format1.parse(dateString);
                } catch (ParseException pe) {
                    return format2.parse(dateString);
                }
            } else {
                return null;
            }
        }
    }
}