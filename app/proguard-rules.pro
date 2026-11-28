# Cobre Mobile — regras R8 (release).
# Media3, Compose e ML Kit/Play Services trazem suas próprias regras de consumidor.

# Mantém números de linha legíveis em stack traces de crashes.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Serviço de mídia referenciado pelo manifest (já mantido pelo AAPT, explícito por segurança).
-keep class app.cobre.mobile.PlaybackService { *; }
