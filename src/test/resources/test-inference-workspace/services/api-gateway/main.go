package main

import (
    "log"
    "github.com/gin-gonic/gin"
)

func main() {
    r := gin.Default()
    r.GET("/health", func(c *gin.Context) {
        c.JSON(200, gin.H{
            "status": "healthy",
        })
    })
    
    log.Println("Starting API Gateway on :8080")
    r.Run(":8080")
}