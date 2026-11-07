# WarLike OSA

WarLike OSA est une application Android native servant d'espace d'accueil pour l'écosystème WarLike. Elle peut être choisie comme application d'accueil dans les paramètres Android et donne accès aux applications installées ainsi qu'aux services WarLike.

Ce projet est un lanceur Android : il s'exécute sur Android et ne remplace pas le noyau ou le système de l'appareil. Les services web s'ouvrent dans le navigateur. OSA et Android Hub sont des applications distinctes, basées sur un catalogue initial commun.

## Construction

Android Studio, JDK 17, Android SDK 35 et Gradle 8.9 :

`gradle :app:assembleDebug`

GitHub Actions construit un APK de test. La signature de distribution, les tests sur téléphone et une release publique restent à effectuer. Pour revenir à un autre accueil : paramètres Android > Applications par défaut > Application d'accueil.
