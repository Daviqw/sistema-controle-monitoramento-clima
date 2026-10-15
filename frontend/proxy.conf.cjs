// Este arquivo roda no Node; a chave não faz parte do bundle Angular.
const origin = process.env.FRONTEND_ORIGIN || 'http://localhost:4200';
const headers = { 'X-Frontend-Key': process.env.FRONTEND_SHARED_KEY || 'dev-only-frontend-key', Origin: origin };
module.exports = {
  '/api': { target: 'http://127.0.0.1:8080', secure: false, headers },
  '/actuator': { target: 'http://127.0.0.1:8080', secure: false, headers }
};
