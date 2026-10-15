import { HttpInterceptorFn } from '@angular/common/http';
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = sessionStorage.getItem('clima_token');
  if (token && (request.url.startsWith('/api/') || request.url.startsWith('/actuator/'))) {
    request = request.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(request);
};
