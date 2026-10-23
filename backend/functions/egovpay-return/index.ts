Deno.serve(()=>new Response('<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>GoServe test payment</title><h1>Return to GoServe</h1><p>Open GoServe and check your payment status. This page does not confirm payment.</p></html>',{
  headers:{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','Content-Security-Policy':"default-src 'none'; frame-ancestors 'none'",'X-Content-Type-Options':'nosniff'},
}));
