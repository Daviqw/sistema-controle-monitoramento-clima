package br.edu.ucsal.clima.security;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import static org.junit.jupiter.api.Assertions.*;
class JwtServiceTest {
 @Test void tokenNaoPermiteContaInativa() {
  var jwt=new JwtService("segredo-de-teste-com-mais-de-trinta-e-dois-bytes",60000);
  var ativo=User.withUsername("usuario").password("hash").roles("USUARIO").build();
  var inativo=User.withUsername("usuario").password("hash").roles("USUARIO").disabled(true).build();
  String token=jwt.gerar(ativo);assertTrue(jwt.valido(token,ativo));assertFalse(jwt.valido(token,inativo));
 }
}
