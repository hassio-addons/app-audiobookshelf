server {
    listen {{ .interface }}:{{ .port }} default_server;

    include /etc/nginx/includes/server_params.conf;
    include /etc/nginx/includes/proxy_params.conf;

    # A route that exists as a directory in the generated client, /login among
    # them, is answered by a redirect that adds a trailing slash, and the one
    # that starts playback redirects to the track it resolved. Both are built
    # out of the request, which arrives here without the Ingress path on it.
    absolute_redirect off;
    proxy_redirect http://$http_host/ $http_x_ingress_path/;
    proxy_redirect https://$http_host/ $http_x_ingress_path/;
    proxy_redirect / $http_x_ingress_path/;

    # The client is built for the site root, which is Home Assistant's under
    # Ingress rather than Audiobookshelf's, so the addresses it produces would
    # never arrive. The path they belong under is settled per request, and is
    # written into the page on the way past.
    #
    # This is a handful of values in an 8KB document, not a rewrite of the
    # application. Nuxt reads "basePath" and "assetsPath" back at runtime and
    # resolves the router and every lazily loaded chunk against them, axios
    # takes its base address from the same object, and the patched client
    # resolves the addresses the browser follows on its own out of
    # "routerBasePath". The megabytes of JavaScript are never touched.
    sub_filter_once off;

    sub_filter '<base href="/">' '<base href="$http_x_ingress_path/">';
    sub_filter 'href="/_nuxt/' 'href="$http_x_ingress_path/_nuxt/';
    sub_filter 'src="/_nuxt/' 'src="$http_x_ingress_path/_nuxt/';
    sub_filter 'href="/favicon.ico"' 'href="$http_x_ingress_path/favicon.ico"';
    sub_filter 'href="/icon.svg"' 'href="$http_x_ingress_path/icon.svg"';
    sub_filter 'href="/icon192.png"' 'href="$http_x_ingress_path/icon192.png"';
    sub_filter 'href="/ios_icon.png"' 'href="$http_x_ingress_path/ios_icon.png"';

    sub_filter 'routerBasePath:""' 'routerBasePath:"$http_x_ingress_path"';
    sub_filter 'axios:{baseURL:""}' 'axios:{baseURL:"$http_x_ingress_path"}';
    sub_filter
        '_app:{basePath:"/",assetsPath:"/_nuxt/"'
        '_app:{basePath:"$http_x_ingress_path/",assetsPath:"$http_x_ingress_path/_nuxt/"';

    location / {
        allow   172.30.32.2;
        deny    all;

        proxy_pass http://backend;
    }
}
