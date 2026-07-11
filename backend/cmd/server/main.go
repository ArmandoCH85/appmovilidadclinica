// Package main es el entry point del backend de transporte_corporativo_mvp.
//
// Cablea las dependencias (DB, router, jobs, auth) y arranca el servidor HTTP
// con graceful shutdown. Sigue el principio de "composition root": este es
// el unico punto del binario que sabe como ensamblar todo; el resto del
// codigo son modulos con dependencias inyectadas.
package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/go-chi/jwtauth/v5"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/modules/admin"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/modules/auth"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/modules/booking"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/modules/driver"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/modules/trips"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/platform/database"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/platform/jobs"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/platform/server"

	_ "github.com/go-sql-driver/mysql"
)

func main() {
	// slog a stdout con nivel INFO por defecto. Suficiente para un MVP en un
	// VPS con logs via journalctl/docker logs.
	slog.SetDefault(slog.New(slog.NewTextHandler(os.Stdout, nil)))

	cfg := database.LoadConfig()
	jwtSecret := os.Getenv("JWT_SECRET")
	if jwtSecret == "" {
		slog.Error("JWT_SECRET no definido en el entorno")
		os.Exit(1)
	}
	port := os.Getenv("HTTP_PORT")
	if port == "" {
		port = "8080"
	}
	migrationsDir := os.Getenv("MIGRATIONS_DIR")
	if migrationsDir == "" {
		migrationsDir = "./migrations"
	}

	// 1. Pool de conexiones a MariaDB.
	db, err := database.NewPool(cfg)
	if err != nil {
		slog.Error("conectando a la base de datos", "error", err)
		os.Exit(1)
	}

	// 2. Aplicar migraciones idempotentes (DROP IF EXISTS + CREATE).
	slog.Info("aplicando migraciones", "dir", migrationsDir)
	if err := database.RunMigrations(db, migrationsDir); err != nil {
		slog.Error("ejecutando migraciones", "error", err)
		os.Exit(1)
	}
	slog.Info("migraciones aplicadas")

	// 3. Firmador/verificador JWT compartido entre auth (emision) y el
	//    router (verificacion via jwtauth.Verifier).
	tokenAuth := jwtauth.New("HS256", []byte(jwtSecret), nil)

	// 4. Grafo de servicios: repositorio -> servicio -> handler por modulo.
	authRepo := auth.NewRepository(db)
	authSvc := auth.NewService(authRepo, jwtSecret)
	authHandler := auth.NewHandler(authSvc)

	tripsRepo := trips.NewRepository(db)
	tripsSvc := trips.NewService(tripsRepo)
	tripsHandler := trips.NewHandler(tripsSvc)

	bookingRepo := booking.NewRepository(db)
	bookingSvc := booking.NewService(bookingRepo)
	bookingHandler := booking.NewHandler(bookingSvc)

	driverRepo := driver.NewRepository(db)
	driverSvc := driver.NewService(driverRepo)
	driverHandler := driver.NewHandler(driverSvc)

	adminRepo := admin.NewRepository(db)
	adminSvc := admin.NewService(adminRepo)
	adminHandler := admin.NewHandler(adminSvc, nil)

	// 5. Router raiz con todas las dependencias inyectadas.
	router := server.NewRouter(server.RouterDeps{
		AuthHandler:    authHandler,
		TripsHandler:   tripsHandler,
		BookingHandler: bookingHandler,
		DriverHandler:  driverHandler,
		AdminHandler:   adminHandler,
		TokenAuth:      tokenAuth,
		Logger:         slog.Default(),
	})

	// 6. Jobs en segundo plano. Un context raiz cancelable les permite
	//    detenerse limpiamente en el graceful shutdown.
	rootCtx, rootCancel := context.WithCancel(context.Background())
	defer rootCancel()
	go jobs.StartTripGenerator(rootCtx, db)
	go jobs.StartNoShowChecker(rootCtx, db)

	// 7. Servidor HTTP con timeouts razonables para un VPS pequeno.
	srv := &http.Server{
		Addr:              ":" + port,
		Handler:           router,
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       120 * time.Second,
	}
	go func() {
		slog.Info("servidor escuchando", "puerto", port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("servidor falló", "error", err)
			os.Exit(1)
		}
	}()

	// 8. Graceful shutdown: SIGINT/SIGTERM cancela jobs y apaga el HTTP.
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh
	slog.Info("apagando servidor")

	rootCancel()
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		slog.Error("error en shutdown", "error", err)
	}
	if err := db.Close(); err != nil {
		slog.Error("error cerrando pool de BD", "error", err)
	}
	slog.Info("servidor detenido")
}
