package br.edu.ucsal.clima.security;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
class FrontendOriginFilterTest {
 @Test void proxyAutorizadoPodeEncaminhar() throws Exception {
  var req=new MockHttpServletRequest("GET","/api/v1/estacoes");req.addHeader("Origin","http://localhost:4200");req.addHeader("X-Frontend-Key","chave");
  var res=new MockHttpServletResponse();var chain=new MockFilterChain();new FrontendOriginFilter("http://localhost:4200","chave").doFilter(req,res,chain);assertNotNull(chain.getRequest());
 }
 @Test void origemComPrefixoParecidoERecusada() throws Exception {
  var req=new MockHttpServletRequest("GET","/api/v1/estacoes");req.addHeader("Origin","http://localhost:4200.evil.example");req.addHeader("X-Frontend-Key","chave");
  var res=new MockHttpServletResponse();var chain=new MockFilterChain();new FrontendOriginFilter("http://localhost:4200","chave").doFilter(req,res,chain);assertEquals(403,res.getStatus());assertNull(chain.getRequest());
 }
 @Test void semChaveNaoAcessaApi() throws Exception {
  var req=new MockHttpServletRequest("GET","/api/v1/estacoes");req.addHeader("Origin","http://localhost:4200");var res=new MockHttpServletResponse();
  new FrontendOriginFilter("http://localhost:4200","chave").doFilter(req,res,new MockFilterChain());assertEquals(403,res.getStatus());
 }
}
