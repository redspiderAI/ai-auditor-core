package middleware

import (
	"net/http"

	"github.com/labstack/echo/v4"
	"github.com/labstack/echo/v4/middleware"
)

// CustomHTTPErrorHandler provides custom error handling
func CustomHTTPErrorHandler() echo.HTTPErrorHandler {
	return func(err error, c echo.Context) {
		// Log the error
		c.Logger().Error(err)

		// Send generic error response
		if c.Response().Committed {
			return
		}

		errObj := map[string]string{
			"error": "Internal Server Error",
		}

		if he, ok := err.(*echo.HTTPError); ok {
			errObj["error"] = he.Message.(string)
			if he.Code != 0 {
				c.JSON(he.Code, errObj)
				return
			}
		}

		c.JSON(http.StatusInternalServerError, errObj)
	}
}

// GetDefaultMiddleware returns a set of commonly used middlewares
func GetDefaultMiddleware() []echo.MiddlewareFunc {
	return []echo.MiddlewareFunc{
		// Logger middleware
		middleware.Logger(),
		
		// Recover middleware
		middleware.Recover(),
		
		// CORS middleware
		middleware.CORS(),
		
		// Request ID middleware
		middleware.RequestID(),
		
		// Timeout middleware
		middleware.TimeoutWithConfig(middleware.TimeoutConfig{
			ErrorMessage: "Request timeout",
		}),
	}
}