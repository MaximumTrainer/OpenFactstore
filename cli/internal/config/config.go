package config

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"

	"github.com/spf13/viper"
)

const (
	defaultConfigFile = ".factstore.yaml"
	KeyHost           = "host"
	KeyToken          = "token"
	KeyQueryHost      = "query_host"
)

// Config holds CLI configuration values.
type Config struct {
	Host      string
	Token     string
	QueryHost string
}

// Load initializes Viper and returns the current configuration.
// A missing config file is not an error; any other read failure is.
func Load() (*Config, error) {
	viper.SetConfigName(".factstore")
	viper.SetConfigType("yaml")
	viper.AddConfigPath("$HOME")

	home, err := os.UserHomeDir()
	if err != nil {
		return nil, fmt.Errorf("cannot determine home directory: %w", err)
	}
	viper.AddConfigPath(home)

	viper.SetEnvPrefix("FACTSTORE")
	viper.AutomaticEnv()

	if err := viper.ReadInConfig(); err != nil {
		var notFound viper.ConfigFileNotFoundError
		if !errors.As(err, &notFound) {
			return nil, fmt.Errorf("read config file: %w", err)
		}
		// Config file doesn't exist yet — that's fine.
	}

	return &Config{
		Host:      viper.GetString(KeyHost),
		Token:     viper.GetString(KeyToken),
		QueryHost: viper.GetString(KeyQueryHost),
	}, nil
}

// Save writes host and token to ~/.factstore.yaml with owner-only permissions
// (0600) to prevent token disclosure.
func Save(host, token string) error {
	home, err := os.UserHomeDir()
	if err != nil {
		return fmt.Errorf("cannot determine home directory: %w", err)
	}
	path := filepath.Join(home, defaultConfigFile)

	// Create the file with 0600 permissions if it doesn't already exist,
	// so that the bearer token is never world-readable.
	if _, statErr := os.Stat(path); os.IsNotExist(statErr) {
		f, createErr := os.OpenFile(path, os.O_CREATE|os.O_WRONLY, 0o600)
		if createErr != nil {
			return fmt.Errorf("cannot create config file: %w", createErr)
		}
		if closeErr := f.Close(); closeErr != nil {
			return fmt.Errorf("cannot close config file: %w", closeErr)
		}
	} else if statErr != nil {
		return fmt.Errorf("cannot stat config file: %w", statErr)
	}

	viper.Set(KeyHost, host)
	viper.Set(KeyToken, token)
	viper.SetConfigFile(path)

	if err := viper.WriteConfig(); err != nil {
		return fmt.Errorf("cannot write config file: %w", err)
	}

	// Enforce 0600 in case the file already existed with broader permissions.
	if err := os.Chmod(path, 0o600); err != nil {
		return fmt.Errorf("cannot set permissions on config file: %w", err)
	}

	return nil
}
