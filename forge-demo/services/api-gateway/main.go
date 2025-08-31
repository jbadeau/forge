package main

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spf13/viper"
	"my-org/go-utils/v1"
)

type HealthResponse struct {
	Status  string `json:"status"`
	Service string `json:"service"`
}

func main() {
	// Initialize configuration
	viper.SetDefault("port", "8080")
	viper.AutomaticEnv()

	// Initialize string utils
	stringUtils := utils.NewStringUtils()

	// Initialize Gin router
	r := gin.Default()

	// Health check endpoint
	r.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, HealthResponse{
			Status:  "healthy",
			Service: stringUtils.Capitalize("api-gateway"),
		})
	})

	// API routes
	v1 := r.Group("/api/v1")
	{
		v1.GET("/status", func(c *gin.Context) {
			c.JSON(http.StatusOK, gin.H{
				"message": stringUtils.Reverse("ecivres yb decivreS"),
				"version": "1.0.0",
			})
		})
	}

	// Start server
	port := viper.GetString("port")
	r.Run(":" + port)
}