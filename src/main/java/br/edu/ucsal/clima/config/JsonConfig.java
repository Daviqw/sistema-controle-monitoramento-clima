package br.edu.ucsal.clima.config;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.*;
import java.io.IOException;

@Configuration
public class JsonConfig {
 @Bean public com.fasterxml.jackson.databind.Module postgresArrays() {
  var module=new SimpleModule();
  module.addSerializer(java.sql.Array.class,new JsonSerializer<java.sql.Array>() {
   @Override public void serialize(java.sql.Array value,JsonGenerator gen,SerializerProvider provider) throws IOException {
    try { gen.writeObject(value.getArray()); }
    catch(java.sql.SQLException ex) { throw new IOException("Não foi possível ler as variáveis",ex); }
   }
  });return module;
 }
}
