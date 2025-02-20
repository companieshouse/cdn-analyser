import { Octokit } from 'octokit';
import dotenv from 'dotenv';
import fs from 'fs';
import yargs from 'yargs';
import { hideBin } from 'yargs/helpers';

dotenv.config();

const octokit = new Octokit({
    auth: process.env.GITHUB_TOKEN,
});

const ACCEPT_HEADER = 'application/vnd.github.v3.text-match+json';

let rateLimitResetTime = null;

const waitForRateLimitReset = async () => {
    if (rateLimitResetTime && new Date() < rateLimitResetTime) {
        const now = new Date();
        const waitTime = rateLimitResetTime - now;
        console.log(`Rate limit exceeded. Waiting for ${waitTime / 1000} seconds.`);
        await new Promise(resolve => setTimeout(resolve, waitTime));
        try {
            const { data: { resources: { core } } } = await octokit.rest.rateLimit.get();
            if (core.remaining === 0) {
                rateLimitResetTime = new Date(core.reset * 1000);
                const waitTime = rateLimitResetTime - new Date();
                console.log(`Rate limit exceeded. Waiting for ${waitTime / 1000} seconds.`);
                await new Promise(resolve => setTimeout(resolve, waitTime));
            } else {
                rateLimitResetTime = null;
            }
        } catch (error) {
            console.error('Error fetching rate limit:', error);
            rateLimitResetTime = new Date(Date.now() + 60000); // Wait for 1 minute before retrying
            const waitTime = rateLimitResetTime - new Date();
            console.log(`Waiting for ${waitTime / 1000} seconds before retrying.`);
            await new Promise(resolve => setTimeout(resolve, waitTime));
        }
            rateLimitResetTime = null;
    }
};

const escapeMap = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;',
};

const escapeHtml = (unsafe) => {
    return unsafe.replace(/[&<>"']/g, (match) => escapeMap[match]);
};

const searchCode = async (cdnString, page) => {
    await waitForRateLimitReset();
    return octokit.rest.search.code({
        q: `${cdnString} org:companieshouse`,
        per_page: 100,
        page,
        headers: {
            accept: ACCEPT_HEADER
        }
    });
};

const getContent = async (owner, repo, path) => {
    await waitForRateLimitReset();
    const response = await octokit.rest.repos.getContent({
        owner,
        repo,
        path,
        mediaType: {
            format: 'raw',
        },
    });
    return response.data;
};

/**
 * Processes each line of the file content to find matches based on the provided pattern.
 *
 * @param {string[]} lines - The lines of the file content.
 * @param {RegExp} pattern - The regex pattern to match against each line.
 * @param {string} repo - The repository name.
 * @param {string} path - The file path within the repository.
 * @returns {Object[]} - An array of identified assets with details.
 */
const processLines = (lines, pattern, repo, path) => {
    const assets = [];
    lines.forEach((line, index) => {
        // Trim the line and check if it matches the pattern
        const match = line.trim().match(pattern);
        if (match) {
            // If a match is found, add the asset details to the assets array
            assets.push({
                repository: repo,
                filepath: path,
                linenumber: index + 1,
                line: escapeHtml(line.trim()),
                name: match[1]
            });
        }
    });
    return assets;
};

const getAllResults = async (cdnString) => {
    await waitForRateLimitReset();
    const firstResponse = await searchCode(cdnString, 1);
    const allResults = [...firstResponse.data.items];

    const totalCount = firstResponse.data.total_count;
    const totalPages = Math.ceil(totalCount / 100);
    console.log(`${totalCount} potential matches found with ${cdnString}`);

    const pagePromises = [];
    for (let page = 2; page <= totalPages; page++) {
        pagePromises.push(searchCode(cdnString, page));
    }

    const pageResponses = await Promise.all(pagePromises);
    pageResponses.forEach(response => {
        allResults.push(...response.data.items);
    });

    return allResults;
};

const processRecords = async (records, pattern, fileChecklist) => {
    const identifiedAssets = [];

    await waitForRateLimitReset();
    const contentPromises = records.map(async (record) => {
        const { owner: { login: owner }, name: repo } = record.repository;
        const { path } = record;

        if (!fileChecklist.has(`${repo}/${path}`)) {
            await waitForRateLimitReset();
            const content = await getContent(owner, repo, path);
            const lines = content.split('\n');
            const assets = processLines(lines, pattern, repo, path);
            identifiedAssets.push(...assets);
            fileChecklist.add(`${repo}/${path}`);
        }
    });

    await Promise.all(contentPromises);
    return identifiedAssets;
};

/**
 * Retrieves files from asset folders by searching for specific CDN strings.
 *
 * @param {string[]} searchStrings - An array of CDN strings to search for.
 * @returns {Promise<Object[]>} - A promise that resolves to an array of identified assets with details.
 */
const getFilesFromAssetFolders = async (searchStrings) => {
    // Regex pattern to match script tags with a src attribute ending in .js
    // The pattern captures the asset name (JavaScript file name) from the src attribute
    const pattern = /<script\s+[^>]*src=["'][^"']*\/([^"']+\.js)["\']/i;
    const fileChecklist = new Set();
    const allResultsPromises = searchStrings.map(cdnString => getAllResults(cdnString));
    const allResultsArrays = await Promise.all(allResultsPromises);

    const allResults = allResultsArrays.flat();
/**
 * Converts an array of asset data into a markdown table format.
 *
 * @param {Object[]} data - The array of asset data to convert.
 * @returns {string} - The markdown table as a string.
 */
const convertToMarkdownTable = (data) => {

    console.log(identifiedAssets.length);
    return identifiedAssets;
};

const convertToMarkdownTable = (data) => {
    const headers = ['Repository', 'File Path', 'Line Number', 'Line', 'Asset Name'];
    const headerRow = `| ${headers.join(' | ')} |`;
    const separatorRow = `| ${headers.map(() => '---').join(' | ')} |`;
    const dataRows = data.map(row => `| ${row.repository} | ${row.filepath} | ${row.linenumber} | ${row.line} | ${row.name} |`);

    return [headerRow, separatorRow, ...dataRows].join('\n');
};

const argv = yargs(hideBin(process.argv)).array('searchStrings').argv;

(async () => {
    const searchStrings = argv.searchStrings;
    if (!searchStrings || searchStrings.length === 0) {
        console.error('Please provide an array of CDN strings using the --searchStrings option.');
        process.exit(1);
    }

    const results = await getFilesFromAssetFolders(searchStrings);

    if (results.length > 0) {
        const markdownContent = convertToMarkdownTable(results);
        fs.writeFileSync('identified_assets.md', markdownContent, 'utf8');
        console.log('Markdown file has been saved.');
    }
})();