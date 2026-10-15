package br.edu.ucsal.clima.controller;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RelatoriosControllerTest {
 private UsernamePasswordAuthenticationToken auth(String perfil) {
  return new UsernamePasswordAuthenticationToken("teste@clima.local","",List.of(new SimpleGrantedAuthority("ROLE_"+perfil)));
 }
 @Test void tipoInexistenteRetorna404() {
  var c=new RelatoriosController(mock(NamedParameterJdbcTemplate.class));
  assertEquals(404,assertThrows(ResponseStatusException.class,()->c.csv("inexistente",auth("USUARIO"))).getStatusCode().value());
 }
 @Test void usuarioNaoPodeExportarTecnicos() {
  var db=mock(NamedParameterJdbcTemplate.class);var c=new RelatoriosController(db);
  assertEquals(403,assertThrows(ResponseStatusException.class,()->c.csv("tecnicos",auth("USUARIO"))).getStatusCode().value());verifyNoInteractions(db);
 }
 @Test void consultaDoUsuarioRestringeIdentidadeEInformaResponsavel() {
  var db=mock(NamedParameterJdbcTemplate.class);
  when(db.queryForList(anyString(),anyMap())).thenReturn(List.of(Map.of("nome_usuario","=1+1","status","PENDENTE")));
  var resposta=new RelatoriosController(db).csv("uso",auth("USUARIO"));
  verify(db).queryForList(contains("lower(u.email)=lower(:email)"),eq(Map.of("admin",false,"colab",false,"email","teste@clima.local")));
  String csv=new String(resposta.getBody(),java.nio.charset.StandardCharsets.UTF_8);
  assertTrue(csv.contains("teste@clima.local"));assertTrue(csv.contains("'=1+1"));
 }
 @Test void pdfPossuiConteudo() throws Exception {
  var db=mock(NamedParameterJdbcTemplate.class);when(db.queryForList(anyString(),anyMap())).thenReturn(List.of(Map.of("status","APROVADA")));
  var resposta=new RelatoriosController(db).pdf("uso",auth("COLABORADOR"));assertTrue(resposta.getBody().length>100);assertEquals("%PDF",new String(resposta.getBody(),0,4));
 }
}
