/**
 * Utility functions for common operations
 */

export class StringUtils {
  /**
   * Checks if a string is empty or null
   */
  static isEmpty(str: string | null | undefined): boolean {
    return !str || str.trim().length === 0;
  }

  /**
   * Capitalizes the first letter of a string
   */
  static capitalize(str: string): string {
    if (this.isEmpty(str)) {
      return str;
    }
    return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
  }

  /**
   * Reverses a string
   */
  static reverse(str: string): string {
    return str.split('').reverse().join('');
  }

  /**
   * Truncates a string to specified length
   */
  static truncate(str: string, maxLength: number): string {
    if (str.length <= maxLength) {
      return str;
    }
    return str.substring(0, maxLength) + '...';
  }
}

export class ArrayUtils {
  /**
   * Removes duplicates from an array
   */
  static unique<T>(array: T[]): T[] {
    return [...new Set(array)];
  }

  /**
   * Chunks an array into smaller arrays of specified size
   */
  static chunk<T>(array: T[], size: number): T[][] {
    const chunks: T[][] = [];
    for (let i = 0; i < array.length; i += size) {
      chunks.push(array.slice(i, i + size));
    }
    return chunks;
  }
}

export default {
  StringUtils,
  ArrayUtils,
};